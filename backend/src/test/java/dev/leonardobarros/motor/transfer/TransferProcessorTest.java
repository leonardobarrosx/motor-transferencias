package dev.leonardobarros.motor.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.account.AccountNotFoundException;
import dev.leonardobarros.motor.account.AccountRepository;
import dev.leonardobarros.motor.account.AccountStatus;
import dev.leonardobarros.motor.account.PixKeyNotFoundException;
import dev.leonardobarros.motor.ledger.EntryType;
import dev.leonardobarros.motor.ledger.LedgerEntry;
import dev.leonardobarros.motor.ledger.LedgerEntryRepository;
import dev.leonardobarros.motor.outbox.TransferEventPublisher;
import dev.leonardobarros.motor.pix.DictSimulado;
import dev.leonardobarros.motor.support.Accounts;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class TransferProcessorTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private static final UUID KEY = UUID.randomUUID();
    private static final String HASH = "a".repeat(64);

    // ids fixos para controlar qual conta vem "primeiro" na ordem de lock
    private static final UUID LOW = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HIGH = UUID.fromString("ffffffff-0000-0000-0000-000000000002");

    @Mock AccountRepository accounts;
    @Mock TransferRepository transfers;
    @Mock LedgerEntryRepository ledger;
    @Mock TransferEventPublisher events;
    @Mock PixLimits limits;
    @Mock DictSimulado dict;
    @Captor ArgumentCaptor<List<LedgerEntry>> entries;

    TransferProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TransferProcessor(accounts, transfers, ledger, events, limits, dict,
                new TransferMetrics(new SimpleMeterRegistry()), Clock.fixed(NOW, ZoneOffset.UTC));
        // nenhum Pix para outro banco pendente: todo o saldo está disponível
        lenient().when(transfers.sumPendingFrom(any())).thenReturn(BigDecimal.ZERO);
    }

    private Account given(UUID id, String balance, AccountStatus status, String pixKey) {
        Account account = Accounts.with(id, balance, status);
        when(accounts.findByIdForUpdate(id)).thenReturn(Optional.of(account));
        if (pixKey != null) {
            when(accounts.findIdByPixKey(pixKey)).thenReturn(Optional.of(id));
        }
        return account;
    }

    private void transfersAreSaved() {
        when(transfers.saveAndFlush(any(Transfer.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Nested
    class WhenItCompletes {

        @Test
        void movesTheMoneyAndWritesBothLedgerEntries() {
            Account payer = given(LOW, "500.00", AccountStatus.ACTIVE, null);
            Account payee = given(HIGH, "10.00", AccountStatus.ACTIVE, "destino@motor.dev");
            transfersAreSaved();

            Transfer transfer = processor.process(KEY, HASH,
                    new TransferCommand(LOW, "destino@motor.dev", new BigDecimal("120.25"), "aluguel"));

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
            assertThat(transfer.getIdempotencyKey()).isEqualTo(KEY);
            assertThat(transfer.getCreatedAt()).isEqualTo(NOW);
            assertThat(payer.getBalance()).isEqualByComparingTo("379.75");
            assertThat(payee.getBalance()).isEqualByComparingTo("130.25");

            verify(ledger).saveAll(entries.capture());
            assertThat(entries.getValue())
                    .extracting(LedgerEntry::getAccountId, LedgerEntry::getType, LedgerEntry::getAmount,
                            LedgerEntry::getBalanceAfter, LedgerEntry::getCounterpartyAccountId)
                    .containsExactly(
                            tuple(LOW, EntryType.DEBIT, new BigDecimal("120.25"), new BigDecimal("379.75"), HIGH),
                            tuple(HIGH, EntryType.CREDIT, new BigDecimal("120.25"), new BigDecimal("130.25"), LOW));
        }

        @Test
        void persistsTheTransferBeforeTouchingBalances() {
            given(LOW, "500.00", AccountStatus.ACTIVE, null);
            given(HIGH, "0.00", AccountStatus.ACTIVE, "destino@motor.dev");
            transfersAreSaved();

            processor.process(KEY, HASH, new TransferCommand(LOW, "destino@motor.dev", BigDecimal.TEN, null));

            InOrder order = inOrder(transfers, ledger, events);
            order.verify(transfers).saveAndFlush(any(Transfer.class));
            order.verify(ledger).saveAll(anyList());
            order.verify(events).record(any(Transfer.class), any(Account.class), any(Account.class), any(Instant.class), isNull());
        }
    }

    @Nested
    class LockOrdering {

        @Test
        void locksLowerIdFirstWhenItIsThePayer() {
            given(LOW, "100.00", AccountStatus.ACTIVE, null);
            given(HIGH, "0.00", AccountStatus.ACTIVE, "alto@motor.dev");
            transfersAreSaved();

            processor.process(KEY, HASH, new TransferCommand(LOW, "alto@motor.dev", BigDecimal.ONE, null));

            InOrder order = inOrder(accounts);
            order.verify(accounts).findByIdForUpdate(LOW);
            order.verify(accounts).findByIdForUpdate(HIGH);
        }

        @Test
        void locksLowerIdFirstWhenItIsThePayee() {
            given(HIGH, "100.00", AccountStatus.ACTIVE, null);
            given(LOW, "0.00", AccountStatus.ACTIVE, "baixo@motor.dev");
            transfersAreSaved();

            processor.process(KEY, HASH, new TransferCommand(HIGH, "baixo@motor.dev", BigDecimal.ONE, null));

            InOrder order = inOrder(accounts);
            order.verify(accounts).findByIdForUpdate(LOW);
            order.verify(accounts).findByIdForUpdate(HIGH);
        }

        @Test
        void ordersLikePostgresNotLikeUuidCompareTo() {
            assertThat(HIGH.compareTo(LOW)).as("UUID.compareTo é com sinal").isNegative();
            assertThat(TransferProcessor.lockOrder(LOW, HIGH)).isNegative();
            assertThat(TransferProcessor.lockOrder(HIGH, LOW)).isPositive();
            assertThat(TransferProcessor.lockOrder(LOW, LOW)).isZero();
        }
    }

    @Nested
    class WhenItIsRejected {

        @Test
        void insufficientFundsIsRecordedWithoutMovingMoney() {
            Account payer = given(LOW, "50.00", AccountStatus.ACTIVE, null);
            Account payee = given(HIGH, "0.00", AccountStatus.ACTIVE, "destino@motor.dev");
            transfersAreSaved();

            Transfer transfer = processor.process(KEY, HASH,
                    new TransferCommand(LOW, "destino@motor.dev", new BigDecimal("50.01"), null));

            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REJECTED);
            assertThat(transfer.getRejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_FUNDS);
            assertThat(payer.getBalance()).isEqualByComparingTo("50.00");
            assertThat(payee.getBalance()).isEqualByComparingTo("0.00");
            verify(ledger, never()).saveAll(anyList());
            // a recusa também vira evento: o pagador é notificado
            verify(events).record(transfer, payer, payee, NOW, null);
        }

        @Test
        void blockedPayer() {
            given(LOW, "500.00", AccountStatus.BLOCKED, null);
            given(HIGH, "0.00", AccountStatus.ACTIVE, "destino@motor.dev");
            transfersAreSaved();

            Transfer transfer = processor.process(KEY, HASH, new TransferCommand(LOW, "destino@motor.dev", BigDecimal.ONE, null));

            assertThat(transfer.getRejectionReason()).isEqualTo(RejectionReason.SOURCE_ACCOUNT_BLOCKED);
        }

        @Test
        void blockedPayee() {
            given(LOW, "500.00", AccountStatus.ACTIVE, null);
            given(HIGH, "0.00", AccountStatus.BLOCKED, "destino@motor.dev");
            transfersAreSaved();

            Transfer transfer = processor.process(KEY, HASH, new TransferCommand(LOW, "destino@motor.dev", BigDecimal.ONE, null));

            assertThat(transfer.getRejectionReason()).isEqualTo(RejectionReason.TARGET_ACCOUNT_BLOCKED);
        }
    }

    @Nested
    class InvalidRequests {

        @Test
        void unknownPixKey() {
            when(accounts.findIdByPixKey("ninguem@motor.dev")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> processor.process(KEY, HASH,
                    new TransferCommand(LOW, "ninguem@motor.dev", BigDecimal.ONE, null)))
                    .isInstanceOf(PixKeyNotFoundException.class);
            verify(transfers, never()).saveAndFlush(any());
        }

        @Test
        void transferToOwnAccount() {
            when(accounts.findIdByPixKey("eu@motor.dev")).thenReturn(Optional.of(LOW));

            assertThatThrownBy(() -> processor.process(KEY, HASH,
                    new TransferCommand(LOW, "eu@motor.dev", BigDecimal.ONE, null)))
                    .isInstanceOf(SameAccountTransferException.class);
            verify(accounts, never()).findByIdForUpdate(any());
        }

        @Test
        void unknownSourceAccount() {
            // a origem tem o menor id, então é a primeira a ser travada e a busca para ali
            when(accounts.findIdByPixKey("destino@motor.dev")).thenReturn(Optional.of(HIGH));
            when(accounts.findByIdForUpdate(LOW)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> processor.process(KEY, HASH,
                    new TransferCommand(LOW, "destino@motor.dev", BigDecimal.ONE, null)))
                    .isInstanceOf(AccountNotFoundException.class);
        }
    }
}

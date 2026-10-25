package dev.leonardobarros.motor.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.account.AccountRepository;
import dev.leonardobarros.motor.support.Accounts;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final UUID KEY = UUID.randomUUID();

    @Mock TransferRepository transfers;
    @Mock AccountRepository accounts;
    @Mock TransferProcessor processor;
    @Mock TransferMetrics metrics;
    @InjectMocks TransferService service;

    private Account payer;
    private Account payee;
    private TransferCommand command;

    @BeforeEach
    void setUp() {
        payer = Accounts.active("100.00");
        payee = Accounts.active("0.00");
        command = new TransferCommand(payer.getId(), payee.getPixKey(), new BigDecimal("25.00"), null);
    }

    private Transfer stored(TransferCommand original) {
        return Transfer.completed(KEY, original.fingerprint(), original, payee.getId(), Instant.now());
    }

    private void accountsResolve() {
        when(accounts.findById(payer.getId())).thenReturn(Optional.of(payer));
        when(accounts.findById(payee.getId())).thenReturn(Optional.of(payee));
    }

    @Test
    void firstRequestIsProcessed() {
        Transfer created = stored(command);
        when(transfers.findBySourceAccountIdAndIdempotencyKey(payer.getId(), KEY)).thenReturn(Optional.empty());
        when(processor.process(KEY, command.fingerprint(), command)).thenReturn(created);
        accountsResolve();

        TransferResult result = service.execute(KEY, command);

        assertThat(result.replayed()).isFalse();
        assertThat(result.transfer().id()).isEqualTo(created.getId());
        assertThat(result.transfer().payee().holderName()).isEqualTo(payee.getHolderName());
    }

    @Test
    void sameKeySamePayloadReplaysWithoutProcessingAgain() {
        Transfer original = stored(command);
        when(transfers.findBySourceAccountIdAndIdempotencyKey(payer.getId(), KEY)).thenReturn(Optional.of(original));
        accountsResolve();

        TransferResult result = service.execute(KEY, command);

        assertThat(result.replayed()).isTrue();
        assertThat(result.transfer().id()).isEqualTo(original.getId());
        verify(processor, never()).process(any(), anyString(), any());
    }

    @Test
    void sameKeyDifferentPayloadIsRefused() {
        when(transfers.findBySourceAccountIdAndIdempotencyKey(payer.getId(), KEY)).thenReturn(Optional.of(stored(command)));
        var different = new TransferCommand(payer.getId(), payee.getPixKey(), new BigDecimal("99.00"), null);

        assertThatThrownBy(() -> service.execute(KEY, different))
                .isInstanceOf(IdempotencyKeyReuseException.class);
        verify(processor, never()).process(any(), anyString(), any());
    }

    @Test
    void losingAConcurrentRaceReturnsTheWinnersTransfer() {
        Transfer winner = stored(command);
        when(transfers.findBySourceAccountIdAndIdempotencyKey(payer.getId(), KEY))
                .thenReturn(Optional.empty())       // as duas requisições passam pela leitura inicial
                .thenReturn(Optional.of(winner));   // depois do rollback, a vencedora já está gravada
        when(processor.process(KEY, command.fingerprint(), command))
                .thenThrow(new DataIntegrityViolationException("uk_transfers_source_idempotency_key"));
        accountsResolve();

        TransferResult result = service.execute(KEY, command);

        assertThat(result.replayed()).isTrue();
        assertThat(result.transfer().id()).isEqualTo(winner.getId());
    }

    @Test
    void integrityViolationUnrelatedToIdempotencyIsNotSwallowed() {
        var violation = new DataIntegrityViolationException("ck_accounts_balance_non_negative");
        when(transfers.findBySourceAccountIdAndIdempotencyKey(payer.getId(), KEY)).thenReturn(Optional.empty());
        when(processor.process(KEY, command.fingerprint(), command)).thenThrow(violation);

        assertThatThrownBy(() -> service.execute(KEY, command)).isSameAs(violation);
    }
}

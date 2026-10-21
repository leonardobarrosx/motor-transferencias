package dev.leonardobarros.motor.transfer;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.account.AccountNotFoundException;
import dev.leonardobarros.motor.account.AccountRepository;
import dev.leonardobarros.motor.account.PixKeyNotFoundException;
import dev.leonardobarros.motor.ledger.LedgerEntry;
import dev.leonardobarros.motor.ledger.LedgerEntryRepository;

/**
 * A transferência propriamente dita, numa única transação ACID:
 * trava as duas contas, decide, movimenta os saldos e grava a ordem e os dois
 * lançamentos. Ou tudo isso é persistido, ou nada é.
 */
@Component
class TransferProcessor {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository ledger;
    private final Clock clock;

    TransferProcessor(AccountRepository accounts, TransferRepository transfers,
                      LedgerEntryRepository ledger, Clock clock) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.ledger = ledger;
        this.clock = clock;
    }

    @Transactional
    Transfer process(UUID idempotencyKey, String requestHash, TransferCommand command) {
        // Só o id: carregar a entidade antes do lock deixaria no contexto de
        // persistência um saldo lido sem trava, que o SELECT ... FOR UPDATE não atualiza.
        UUID payeeId = accounts.findIdByPixKey(command.targetPixKey())
                .orElseThrow(PixKeyNotFoundException::new);
        if (payeeId.equals(command.sourceAccountId())) {
            throw new SameAccountTransferException();
        }

        LockedAccounts locked = lockInConsistentOrder(command.sourceAccountId(), payeeId);
        Account payer = locked.payer();
        Account payee = locked.payee();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        Optional<RejectionReason> rejection = evaluate(payer, payee, command);
        if (rejection.isPresent()) {
            return transfers.saveAndFlush(
                    Transfer.rejected(idempotencyKey, requestHash, command, payeeId, rejection.get(), now));
        }

        // saveAndFlush antes de mexer nos saldos: se outra requisição com a mesma chave
        // já tiver sido gravada, a violação de unicidade aparece aqui e a transação inteira volta.
        Transfer transfer = transfers.saveAndFlush(
                Transfer.completed(idempotencyKey, requestHash, command, payeeId, now));

        payer.debit(command.amount(), now);
        payee.credit(command.amount(), now);
        ledger.saveAll(List.of(
                LedgerEntry.debit(payer, payee.getId(), transfer.getId(), command.amount(), now),
                LedgerEntry.credit(payee, payer.getId(), transfer.getId(), command.amount(), now)));
        return transfer;
    }

    /**
     * Toda transação trava as contas pela mesma ordem (menor UUID primeiro).
     * Sem isso, A→B e B→A simultâneas travariam uma conta cada e esperariam a
     * outra para sempre: o deadlock clássico de transferência.
     */
    private LockedAccounts lockInConsistentOrder(UUID payerId, UUID payeeId) {
        boolean payerFirst = lockOrder(payerId, payeeId) < 0;
        UUID firstId = payerFirst ? payerId : payeeId;
        UUID secondId = payerFirst ? payeeId : payerId;

        Account first = accounts.findByIdForUpdate(firstId).orElseThrow(() -> new AccountNotFoundException(firstId));
        Account second = accounts.findByIdForUpdate(secondId).orElseThrow(() -> new AccountNotFoundException(secondId));
        return payerFirst ? new LockedAccounts(first, second) : new LockedAccounts(second, first);
    }

    /**
     * Comparação sem sinal, igual à do Postgres. {@link UUID#compareTo} compara com
     * sinal (ffffffff-... vem antes de 00000000-...), o que divergiria de qualquer
     * {@code ORDER BY id FOR UPDATE} feito em SQL e reabriria a porta para deadlock.
     */
    static int lockOrder(UUID a, UUID b) {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }

    private static Optional<RejectionReason> evaluate(Account payer, Account payee, TransferCommand command) {
        if (!payer.isActive()) {
            return Optional.of(RejectionReason.SOURCE_ACCOUNT_BLOCKED);
        }
        if (!payee.isActive()) {
            return Optional.of(RejectionReason.TARGET_ACCOUNT_BLOCKED);
        }
        if (!payer.canDebit(command.amount())) {
            return Optional.of(RejectionReason.INSUFFICIENT_FUNDS);
        }
        return Optional.empty();
    }

    private record LockedAccounts(Account payer, Account payee) {
    }
}

package dev.leonardobarros.motor.transfer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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
import dev.leonardobarros.motor.account.SettlementAccount;
import dev.leonardobarros.motor.ledger.LedgerEntry;
import dev.leonardobarros.motor.ledger.LedgerEntryRepository;
import dev.leonardobarros.motor.outbox.TransferEventPublisher;
import dev.leonardobarros.motor.pix.ChaveExterna;
import dev.leonardobarros.motor.pix.DictSimulado;

/**
 * A transferência propriamente dita, numa única transação ACID:
 * trava as duas contas, decide, movimenta os saldos e grava a ordem, os dois
 * lançamentos e o evento do outbox. Ou tudo isso é persistido, ou nada é.
 */
@Component
class TransferProcessor {

    /** Prazo para devolver um Pix recebido. */
    static final Duration REFUND_WINDOW = Duration.ofDays(90);

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final LedgerEntryRepository ledger;
    private final TransferEventPublisher events;
    private final PixLimits limits;
    private final DictSimulado dict;
    private final TransferMetrics metrics;
    private final Clock clock;

    TransferProcessor(AccountRepository accounts, TransferRepository transfers, LedgerEntryRepository ledger,
                      TransferEventPublisher events, PixLimits limits, DictSimulado dict, TransferMetrics metrics,
                      Clock clock) {
        this.accounts = accounts;
        this.dict = dict;
        this.transfers = transfers;
        this.ledger = ledger;
        this.events = events;
        this.limits = limits;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    Transfer process(UUID idempotencyKey, String requestHash, TransferCommand command) {
        // Só o id: carregar a entidade antes do lock deixaria no contexto de
        // persistência um saldo lido sem trava, que o SELECT ... FOR UPDATE não atualiza.
        Optional<UUID> internalPayee = accounts.findIdByPixKey(command.targetPixKey());
        if (internalPayee.isEmpty()) {
            // não é daqui: o DICT diz em qual instituição a chave está
            ChaveExterna external = dict.consultar(command.targetPixKey()).orElseThrow(PixKeyNotFoundException::new);
            return toOtherInstitution(idempotencyKey, requestHash, command, external);
        }
        UUID payeeId = internalPayee.get();
        if (payeeId.equals(command.sourceAccountId())) {
            throw new SameAccountTransferException();
        }

        LockedAccounts locked = lockInConsistentOrder(command.sourceAccountId(), payeeId);
        Account payer = locked.payer();
        Account payee = locked.payee();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        // saldo antes de limite: "saldo insuficiente" é o motivo mais básico e o mais útil para quem paga
        Optional<RejectionReason> rejection = evaluate(payer, payee.isActive(), command.amount(), onHold(payer));
        if (rejection.isEmpty() && limits.wouldExceed(payer, command.amount(), now)) {
            rejection = Optional.of(RejectionReason.LIMIT_EXCEEDED);
        }
        if (rejection.isPresent()) {
            Transfer rejected = transfers.saveAndFlush(
                    Transfer.rejected(idempotencyKey, requestHash, command, payeeId, rejection.get(), now));
            events.record(rejected, payer, payee, now, null);
            return rejected;
        }

        // saveAndFlush antes de mexer nos saldos: se outra requisição com a mesma chave
        // já tiver sido gravada, a violação de unicidade aparece aqui e a transação inteira volta.
        Transfer transfer = transfers.saveAndFlush(
                Transfer.completed(idempotencyKey, requestHash, command, payeeId, now));
        settle(transfer, payer, payee, now, null);
        return transfer;
    }

    /**
     * Pix para outra instituição: valida aqui (bloqueio, saldo disponível, limite) com
     * a conta pagadora travada e grava PENDING. Nenhum lançamento: o valor fica
     * reservado até o SPI responder, e o evento leva o pedido ao motor-integracoes.
     */
    private Transfer toOtherInstitution(UUID idempotencyKey, String requestHash, TransferCommand command,
                                        ChaveExterna payee) {
        Account payer = accounts.findByIdForUpdate(command.sourceAccountId())
                .orElseThrow(() -> new AccountNotFoundException(command.sourceAccountId()));
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        Optional<RejectionReason> rejection = evaluate(payer, true, command.amount(), onHold(payer));
        if (rejection.isEmpty() && limits.wouldExceed(payer, command.amount(), now)) {
            rejection = Optional.of(RejectionReason.LIMIT_EXCEEDED);
        }
        Transfer transfer = transfers.saveAndFlush(Transfer.toOtherInstitution(idempotencyKey, requestHash, command,
                SettlementAccount.ID, payee, rejection.orElse(null), now));
        events.record(transfer, payer, null, now, null);
        return transfer;
    }

    /**
     * A resposta do SPI. Idempotente: o mesmo resultado entregue duas vezes pelo
     * Kafka encontra a transferência já decidida e não faz nada.
     *
     * @return se a resposta mudou a transferência (false = repetida ou atrasada)
     */
    @Transactional
    boolean applySpiResult(SpiResult result) {
        Optional<UUID> payerId = transfers.findSourceAccountId(result.transferId());
        if (payerId.isEmpty()) {
            throw new TransferNotFoundException();
        }
        // mesmas travas e mesma ordem de qualquer Pix; a transferência é lida só depois delas
        LockedAccounts locked = lockInConsistentOrder(payerId.get(), SettlementAccount.ID);
        Transfer transfer = transfers.findById(result.transferId()).orElseThrow(TransferNotFoundException::new);
        if (!transfer.isPending()) {
            return false;
        }
        if (!transfer.getEndToEndId().equals(result.endToEndId())) {
            throw new IllegalArgumentException("Resposta do SPI com E2E " + result.endToEndId()
                    + " para a transferência " + transfer.getEndToEndId());
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (result.settled()) {
            transfer.settle(now);
            settle(transfer, locked.payer(), locked.payee(), now, null);
        } else {
            // compensação: o dinheiro nunca saiu, então basta liberar a reserva
            transfer.rejectBySpi(result.reasonCode(), now);
            events.record(transfer, locked.payer(), locked.payee(), now, null);
        }
        return true;
    }

    /**
     * Devolução: o dinheiro volta de quem recebeu para quem pagou. Mesmas travas e
     * mesma ordem de lock; o total já devolvido é conferido com as duas contas
     * travadas, então duas devoluções simultâneas do mesmo Pix não passam do original.
     */
    @Transactional
    Transfer refund(UUID idempotencyKey, String requestHash, RefundCommand command) {
        // quem não participou do Pix recebe 404, como no comprovante
        Transfer original = transfers.findById(command.originalTransferId())
                .filter(t -> t.getTargetAccountId().equals(command.requesterAccountId())
                        || t.getSourceAccountId().equals(command.requesterAccountId()))
                .orElseThrow(TransferNotFoundException::new);
        if (!original.getTargetAccountId().equals(command.requesterAccountId())
                || original.getKind() != TransferKind.PIX || !original.isCompleted()) {
            throw RefundNotAllowedException.notRefundable();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (original.getCreatedAt().plus(REFUND_WINDOW).isBefore(now)) {
            throw RefundNotAllowedException.expired();
        }

        LockedAccounts locked = lockInConsistentOrder(original.getTargetAccountId(), original.getSourceAccountId());
        Account refunder = locked.payer();
        Account originalPayer = locked.payee();

        if (transfers.sumRefunded(original.getId()).add(command.amount()).compareTo(original.getAmount()) > 0) {
            throw RefundNotAllowedException.exceedsOriginal();
        }

        Optional<RejectionReason> rejection = evaluate(refunder, originalPayer.isActive(), command.amount(),
                onHold(refunder));
        Transfer refund = transfers.saveAndFlush(
                Transfer.refund(idempotencyKey, requestHash, command, original, rejection.orElse(null), now));
        if (rejection.isPresent()) {
            events.record(refund, refunder, originalPayer, now, original.getEndToEndId());
        } else {
            settle(refund, refunder, originalPayer, now, original.getEndToEndId());
        }
        return refund;
    }

    /** Move o dinheiro e grava os dois lançamentos e o evento, na transação corrente. */
    private void settle(Transfer transfer, Account from, Account to, Instant now, String originalEndToEndId) {
        from.debit(transfer.getAmount(), now);
        to.credit(transfer.getAmount(), now);
        ledger.saveAll(List.of(
                LedgerEntry.debit(from, to.getId(), transfer.getId(), transfer.getAmount(), now),
                LedgerEntry.credit(to, from.getId(), transfer.getId(), transfer.getAmount(), now)));
        // o evento entra na mesma transação: commit grava os dois, rollback desfaz os dois
        events.record(transfer, from, to, now, originalEndToEndId);
    }

    /**
     * Toda transação trava as contas pela mesma ordem (menor UUID primeiro).
     * Sem isso, A→B e B→A simultâneas travariam uma conta cada e esperariam a
     * outra para sempre: o deadlock clássico de transferência.
     */
    private LockedAccounts lockInConsistentOrder(UUID payerId, UUID payeeId) {
        return metrics.timeLockWait(() -> lock(payerId, payeeId));
    }

    private LockedAccounts lock(UUID payerId, UUID payeeId) {
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

    /** Reservado em Pix para outros bancos ainda pendentes; lido com a conta travada. */
    private BigDecimal onHold(Account payer) {
        return transfers.sumPendingFrom(payer.getId());
    }

    private static Optional<RejectionReason> evaluate(Account payer, boolean payeeActive, BigDecimal amount,
                                                      BigDecimal onHold) {
        if (!payer.isActive()) {
            return Optional.of(RejectionReason.SOURCE_ACCOUNT_BLOCKED);
        }
        if (!payeeActive) {
            return Optional.of(RejectionReason.TARGET_ACCOUNT_BLOCKED);
        }
        if (!payer.canDebit(amount, onHold)) {
            return Optional.of(RejectionReason.INSUFFICIENT_FUNDS);
        }
        return Optional.empty();
    }

    private record LockedAccounts(Account payer, Account payee) {
    }
}

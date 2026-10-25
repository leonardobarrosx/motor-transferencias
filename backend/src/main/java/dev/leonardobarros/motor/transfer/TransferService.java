package dev.leonardobarros.motor.transfer;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.account.AccountNotFoundException;
import dev.leonardobarros.motor.account.AccountRepository;

/**
 * Porta de entrada das transferências e dona da idempotência.
 *
 * <p>Fica fora da transação de propósito: a corrida entre duas requisições com a
 * mesma Idempotency-Key só se resolve depois que uma delas faz commit, e é aqui,
 * do lado de fora, que a perdedora consegue ler o que a vencedora gravou.
 */
@Service
public class TransferService {

    private final TransferRepository transfers;
    private final AccountRepository accounts;
    private final TransferProcessor processor;
    private final TransferMetrics metrics;

    TransferService(TransferRepository transfers, AccountRepository accounts, TransferProcessor processor,
                    TransferMetrics metrics) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.processor = processor;
        this.metrics = metrics;
    }

    public TransferResult execute(UUID idempotencyKey, TransferCommand command) {
        String requestHash = command.fingerprint();
        return idempotent(command.sourceAccountId(), idempotencyKey, requestHash,
                () -> processor.process(idempotencyKey, requestHash, command));
    }

    /** Devolução: as mesmas garantias de idempotência de um Pix. */
    public TransferResult refund(UUID idempotencyKey, RefundCommand command) {
        String requestHash = command.fingerprint();
        // quem devolve é a origem da devolução: a chave pertence a essa conta
        return idempotent(command.requesterAccountId(), idempotencyKey, requestHash,
                () -> processor.refund(idempotencyKey, requestHash, command));
    }

    /**
     * A chave é única por conta de origem, não no sistema todo: a chave de um cliente
     * não colide com a de outro nem revela que já foi usada.
     */
    private TransferResult idempotent(UUID ownerAccountId, UUID idempotencyKey, String requestHash,
                                      Supplier<Transfer> operation) {
        var existing = transfers.findBySourceAccountIdAndIdempotencyKey(ownerAccountId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), idempotencyKey, requestHash);
        }

        try {
            TransferView processed = toView(operation.get());
            metrics.processed(processed);
            return new TransferResult(processed, false);
        } catch (DataIntegrityViolationException race) {
            // Outra requisição com a mesma chave fez commit primeiro; esta foi desfeita
            // inteira pelo rollback. Responde com o que a vencedora gravou.
            return transfers.findBySourceAccountIdAndIdempotencyKey(ownerAccountId, idempotencyKey)
                    .map(winner -> replay(winner, idempotencyKey, requestHash))
                    .orElseThrow(() -> race);
        }
    }

    /**
     * Só quem enviou ou recebeu vê o comprovante. Para os demais a resposta é 404,
     * não 403: um 403 confirmaria que aquele id existe.
     */
    @Transactional(readOnly = true)
    public TransferView find(UUID transferId, UUID accountId) {
        return transfers.findById(transferId)
                .filter(t -> t.getSourceAccountId().equals(accountId) || t.getTargetAccountId().equals(accountId))
                .map(this::toView)
                .orElseThrow(TransferNotFoundException::new);
    }

    private TransferResult replay(Transfer transfer, UUID idempotencyKey, String requestHash) {
        if (!transfer.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyReuseException(idempotencyKey);
        }
        metrics.replayed();
        return new TransferResult(toView(transfer), true);
    }

    private TransferView toView(Transfer transfer) {
        return new TransferView(
                transfer.getId(),
                transfer.getEndToEndId(),
                transfer.getStatus(),
                transfer.getRejectionReason(),
                transfer.getAmount(),
                transfer.getDescription(),
                party(transfer.getSourceAccountId()),
                party(transfer.getTargetAccountId()),
                transfer.getCreatedAt(),
                transfer.getKind(),
                transfer.getOriginalTransferId(),
                transfer.getKind() == TransferKind.PIX ? transfers.sumRefunded(transfer.getId()) : null);
    }

    private TransferView.Party party(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        return new TransferView.Party(account.getId(), account.getHolderName(), account.getPixKey());
    }
}

package dev.leonardobarros.motor.transfer;

import java.util.UUID;

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

    TransferService(TransferRepository transfers, AccountRepository accounts, TransferProcessor processor) {
        this.transfers = transfers;
        this.accounts = accounts;
        this.processor = processor;
    }

    public TransferResult execute(UUID idempotencyKey, TransferCommand command) {
        String requestHash = command.fingerprint();

        var existing = transfers.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), idempotencyKey, requestHash);
        }

        try {
            return new TransferResult(toView(processor.process(idempotencyKey, requestHash, command)), false);
        } catch (DataIntegrityViolationException race) {
            // Outra requisição com a mesma chave fez commit primeiro; esta foi desfeita
            // inteira pelo rollback. Responde com o que a vencedora gravou.
            return transfers.findByIdempotencyKey(idempotencyKey)
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
                transfer.getCreatedAt());
    }

    private TransferView.Party party(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        return new TransferView.Party(account.getId(), account.getHolderName(), account.getPixKey());
    }
}

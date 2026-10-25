package dev.leonardobarros.motor.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.transfer.EndToEndId;
import dev.leonardobarros.motor.transfer.Transfer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registra o evento da transferência no outbox. Exige uma transação aberta
 * (MANDATORY): chamado fora da transação do TransferProcessor, falha em vez de
 * gravar um evento solto.
 */
@Component
public class TransferEventPublisher {

    static final String AGGREGATE = "Transfer";

    private final OutboxRepository outbox;
    private final JsonMapper json;

    TransferEventPublisher(OutboxRepository outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Transfer transfer, Account payer, Account payee, Instant now) {
        String type = transfer.isCompleted() ? TransferEvent.COMPLETED : TransferEvent.REJECTED;
        TransferEvent event = new TransferEvent(
                UUID.randomUUID(), type, 1, now,
                transfer.getId(), transfer.getEndToEndId(), transfer.getAmount(), "BRL",
                transfer.getDescription(),
                transfer.getRejectionReason() == null ? null : transfer.getRejectionReason().name(),
                party(payer), party(payee));
        // chave = conta pagadora: os eventos de uma mesma conta caem na mesma partição, em ordem
        outbox.save(new OutboxEvent(event.eventId(), AGGREGATE, transfer.getId(), type,
                payer.getId().toString(), json.writeValueAsString(event), now));
    }

    private static TransferEvent.Party party(Account account) {
        return new TransferEvent.Party(account.getId(), account.getHolderName(), account.getDocument(),
                EndToEndId.SIMULATOR_ISPB, account.getBranch(), account.getNumber(), account.getPixKey());
    }
}

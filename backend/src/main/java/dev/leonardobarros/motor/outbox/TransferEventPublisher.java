package dev.leonardobarros.motor.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.transfer.EndToEndId;
import dev.leonardobarros.motor.transfer.Transfer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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
    private final ObjectProvider<Tracer> tracer;

    TransferEventPublisher(OutboxRepository outbox, JsonMapper json, ObjectProvider<Tracer> tracer) {
        this.outbox = outbox;
        this.json = json;
        this.tracer = tracer;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Transfer transfer, Account payer, Account payee, Instant now, String originalEndToEndId) {
        // payee é null em Pix para outro banco: quem recebe não tem conta aqui
        String type = switch (transfer.getStatus()) {
            case PENDING -> TransferEvent.SUBMITTED;
            case COMPLETED -> TransferEvent.COMPLETED;
            case REJECTED -> TransferEvent.REJECTED;
        };
        TransferEvent event = new TransferEvent(
                UUID.randomUUID(), type, 1, now,
                transfer.getId(), transfer.getEndToEndId(), transfer.getAmount(), "BRL",
                transfer.getDescription(),
                transfer.getRejectionReason() == null ? null : transfer.getRejectionReason().name(),
                transfer.getKind().name(), transfer.getOriginalTransferId(), originalEndToEndId,
                transfer.getRefundReason() == null ? null : transfer.getRefundReason().isoCode(),
                party(payer), payeeOf(transfer, payee));
        // chave = conta pagadora: os eventos de uma mesma conta caem na mesma partição, em ordem
        outbox.save(new OutboxEvent(event.eventId(), AGGREGATE, transfer.getId(), type,
                payer.getId().toString(), json.writeValueAsString(event), currentTraceParent(), now));
    }

    /** O trace da requisição em andamento, no formato W3C: 00-{traceId}-{spanId}-{flags}. */
    private String currentTraceParent() {
        Tracer current = tracer.getIfAvailable();
        Span span = current == null ? null : current.currentSpan();
        if (span == null) {
            return null;
        }
        var context = span.context();
        return "00-%s-%s-%s".formatted(context.traceId(), context.spanId(),
                Boolean.TRUE.equals(context.sampled()) ? "01" : "00");
    }

    /** Recebedor em outro banco: os dados vêm do DICT, gravados na transferência. */
    private static TransferEvent.Party payeeOf(Transfer transfer, Account payee) {
        return transfer.getExternalPayee()
                .map(external -> new TransferEvent.Party(null, external.holderName(), external.document(),
                        external.ispb(), external.branch(), external.accountNumber(), external.pixKey()))
                .orElseGet(() -> party(payee));
    }

    private static TransferEvent.Party party(Account account) {
        return new TransferEvent.Party(account.getId(), account.getHolderName(), account.getDocument(),
                EndToEndId.SIMULATOR_ISPB, account.getBranch(), account.getNumber(), account.getPixKey());
    }
}

package dev.leonardobarros.motor.outbox;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Contrato publicado no tópico pix.transfers (schemaVersion 1). Quem consome não
 * acessa o banco do motor: tudo que precisa vem aqui. Ver docs/EVENTOS.md.
 */
public record TransferEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID transferId,
        String endToEndId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String currency,
        String description,
        String rejectionReason,
        Party payer,
        Party payee) {

    public static final String COMPLETED = "TransferCompleted";
    public static final String REJECTED = "TransferRejected";

    /** Dados que uma mensagem pacs.008 precisa de cada lado. */
    public record Party(UUID accountId, String holderName, String taxId, String ispb,
                        String branch, String accountNumber, String pixKey) {
    }
}

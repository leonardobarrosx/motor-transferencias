package dev.leonardobarros.integracoes.evento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cópia do lado consumidor do contrato publicado pelo motor (docs/EVENTOS.md).
 * Ignora campos desconhecidos: o produtor pode acrescentar campos sem quebrar
 * este serviço (compatibilidade para frente).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        UUID transferId,
        String endToEndId,
        BigDecimal amount,
        String currency,
        String description,
        String rejectionReason,
        Party payer,
        Party payee) {

    public static final String COMPLETED = "TransferCompleted";
    public static final String REJECTED = "TransferRejected";

    public boolean completed() {
        return COMPLETED.equals(eventType);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Party(UUID accountId, String holderName, String taxId, String ispb,
                        String branch, String accountNumber, String pixKey) {
    }
}

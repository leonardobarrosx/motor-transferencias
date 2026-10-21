package dev.leonardobarros.motor.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Comprovante: o que a API devolve sobre uma transferência. */
public record TransferView(
        UUID id,
        String endToEndId,
        TransferStatus status,
        RejectionReason rejectionReason,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String description,
        Party payer,
        Party payee,
        Instant createdAt) {

    public record Party(UUID accountId, String holderName, String pixKey) {
    }
}

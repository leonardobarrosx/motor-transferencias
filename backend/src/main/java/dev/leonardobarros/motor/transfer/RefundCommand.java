package dev.leonardobarros.motor.transfer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import dev.leonardobarros.motor.shared.Money;

/** Pedido de devolução feito por quem RECEBEU o Pix original. */
public record RefundCommand(UUID requesterAccountId, UUID originalTransferId, BigDecimal amount, RefundReason reason) {

    public RefundCommand {
        Objects.requireNonNull(requesterAccountId, "requesterAccountId");
        Objects.requireNonNull(originalTransferId, "originalTransferId");
        amount = Money.requirePositive(amount);
        reason = reason == null ? RefundReason.CUSTOMER_REQUEST : reason;
    }

    /** Mesmo papel do fingerprint da transferência: detectar a chave reaproveitada com outro pedido. */
    public String fingerprint() {
        String canonical = String.join("\u001F", "REFUND", requesterAccountId.toString(),
                originalTransferId.toString(), amount.toPlainString(), reason.name());
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}

package dev.leonardobarros.motor.transfer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import dev.leonardobarros.motor.shared.Money;

/** Pedido de transferência já validado na borda e normalizado. */
public record TransferCommand(UUID sourceAccountId, String targetPixKey, BigDecimal amount, String description) {

    private static final char SEPARATOR = '\u001F';

    public TransferCommand {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        targetPixKey = Objects.requireNonNull(targetPixKey, "targetPixKey").strip();
        amount = Money.requirePositive(amount);
        description = description == null || description.isBlank() ? null : description.strip();
    }

    /**
     * SHA-256 do pedido normalizado. Junto com a Idempotency-Key, detecta o cliente
     * que reaproveita a mesma chave para um pedido diferente ("100" e "100.00"
     * são o mesmo pedido; outra chave Pix não é).
     */
    public String fingerprint() {
        String canonical = String.join(String.valueOf(SEPARATOR),
                sourceAccountId.toString(),
                targetPixKey,
                amount.toPlainString(),
                description == null ? "" : description);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}

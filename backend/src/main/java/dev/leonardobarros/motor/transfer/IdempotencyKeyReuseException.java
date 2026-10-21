package dev.leonardobarros.motor.transfer;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import dev.leonardobarros.motor.shared.error.DomainException;

/** A mesma Idempotency-Key chegou com um pedido diferente do original. */
public class IdempotencyKeyReuseException extends DomainException {

    private final UUID idempotencyKey;

    public IdempotencyKeyReuseException(UUID idempotencyKey) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED",
                "Esta Idempotency-Key já foi usada para uma transferência com outros dados.");
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("idempotencyKey", idempotencyKey);
    }
}

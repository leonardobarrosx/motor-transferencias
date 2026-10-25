package dev.leonardobarros.motor.ratelimit;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.HttpStatus;

import dev.leonardobarros.motor.shared.error.DomainException;

public class RateLimitedException extends DomainException {

    private final long retryAfterSeconds;

    RateLimitedException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Muitas tentativas. Aguarde um pouco e tente de novo.");
        this.retryAfterSeconds = Math.max(1, (long) Math.ceil(retryAfter.toMillis() / 1000.0));
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("retryAfterSeconds", retryAfterSeconds);
    }
}

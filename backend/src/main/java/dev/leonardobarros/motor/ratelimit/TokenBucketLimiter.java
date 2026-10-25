package dev.leonardobarros.motor.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket em memória: cada chave começa com {@code capacity} fichas, que voltam
 * aos poucos ao longo da janela. Gasta-se uma por requisição; sem ficha, a resposta
 * diz quantos segundos esperar.
 *
 * <p>Escrito à mão porque são trinta linhas e mostram o algoritmo; em produção eu
 * usaria Bucket4j com Redis, ou o rate limit do gateway, para valer entre instâncias.
 */
final class TokenBucketLimiter {

    /** Acima disso, os baldes cheios (chaves inativas) são descartados. */
    private static final int MAX_KEYS = 10_000;

    private final int capacity;
    private final double tokensPerNano;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    TokenBucketLimiter(RateLimitProperties.Rule rule, Clock clock) {
        this.capacity = rule.capacity();
        this.tokensPerNano = rule.capacity() / (double) rule.window().toNanos();
        this.clock = clock;
    }

    /** {@link Duration#ZERO} se passou; senão, quanto falta para a próxima ficha. */
    Duration tryAcquire(String key) {
        long now = nanos();
        if (buckets.size() > MAX_KEYS) {
            buckets.values().removeIf(bucket -> bucket.isFull(now));
        }
        return buckets.computeIfAbsent(key, k -> new Bucket(capacity, now)).take(now);
    }

    private long nanos() {
        var instant = clock.instant();
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    private final class Bucket {

        private double tokens;
        private long updatedAt;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.updatedAt = now;
        }

        synchronized Duration take(long now) {
            refill(now);
            if (tokens >= 1) {
                tokens -= 1;
                return Duration.ZERO;
            }
            return Duration.ofNanos((long) Math.ceil((1 - tokens) / tokensPerNano));
        }

        synchronized boolean isFull(long now) {
            refill(now);
            return tokens >= capacity;
        }

        private void refill(long now) {
            tokens = Math.min(capacity, tokens + (now - updatedAt) * tokensPerNano);
            updatedAt = now;
        }
    }
}

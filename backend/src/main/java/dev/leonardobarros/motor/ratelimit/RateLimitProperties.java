package dev.leonardobarros.motor.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Quantas requisições cabem em cada janela. Os padrões valem para uma instância;
 * com várias, o limite precisa morar num lugar compartilhado (Redis, ou o gateway).
 */
@ConfigurationProperties("motor.rate-limit")
public record RateLimitProperties(Rule login, Rule pixKeyLookup) {

    public RateLimitProperties {
        login = login == null ? new Rule(10, Duration.ofMinutes(1)) : login;
        pixKeyLookup = pixKeyLookup == null ? new Rule(30, Duration.ofMinutes(1)) : pixKeyLookup;
    }

    public record Rule(int capacity, Duration window) {
    }
}

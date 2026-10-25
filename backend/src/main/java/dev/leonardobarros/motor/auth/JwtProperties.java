package dev.leonardobarros.motor.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret chave HMAC do HS256; precisa de pelo menos 256 bits (32 bytes)
 * @param ttl    validade do access token
 */
@ConfigurationProperties(prefix = "motor.security.jwt")
public record JwtProperties(String secret, Duration ttl, String issuer) {

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            // falhar no boot é melhor que assinar tokens com uma chave fraca
            throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 bytes");
        }
        ttl = ttl == null ? Duration.ofMinutes(30) : ttl;
        issuer = issuer == null ? "motor-transferencias" : issuer;
    }
}

package dev.leonardobarros.motor.auth;

/** @param expiresIn segundos até o token expirar */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, String holderName) {
}

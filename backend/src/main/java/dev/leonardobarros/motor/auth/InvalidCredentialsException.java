package dev.leonardobarros.motor.auth;

import org.springframework.http.HttpStatus;

import dev.leonardobarros.motor.shared.error.DomainException;

/** A mesma resposta para e-mail inexistente e senha errada: não revela quem tem conta. */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "E-mail ou senha incorretos.");
    }
}

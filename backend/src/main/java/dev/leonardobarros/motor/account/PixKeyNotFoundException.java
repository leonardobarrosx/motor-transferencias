package dev.leonardobarros.motor.account;

import org.springframework.http.HttpStatus;

import dev.leonardobarros.motor.shared.error.DomainException;

public class PixKeyNotFoundException extends DomainException {

    public PixKeyNotFoundException() {
        // a chave não volta na resposta: ela pode ser um CPF ou telefone de terceiro
        super(HttpStatus.NOT_FOUND, "PIX_KEY_NOT_FOUND", "Nenhuma conta encontrada para a chave Pix informada.");
    }
}

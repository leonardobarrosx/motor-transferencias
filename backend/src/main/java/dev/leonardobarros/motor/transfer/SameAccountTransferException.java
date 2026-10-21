package dev.leonardobarros.motor.transfer;

import org.springframework.http.HttpStatus;

import dev.leonardobarros.motor.shared.error.DomainException;

public class SameAccountTransferException extends DomainException {

    public SameAccountTransferException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "SAME_ACCOUNT_TRANSFER",
                "A chave Pix de destino pertence à própria conta de origem.");
    }
}

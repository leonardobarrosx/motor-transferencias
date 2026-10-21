package dev.leonardobarros.motor.transfer;

import org.springframework.http.HttpStatus;

import dev.leonardobarros.motor.shared.error.DomainException;

public class TransferNotFoundException extends DomainException {

    public TransferNotFoundException() {
        super(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", "Transferência não encontrada.");
    }
}

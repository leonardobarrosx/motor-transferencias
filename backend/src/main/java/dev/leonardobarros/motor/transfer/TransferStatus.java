package dev.leonardobarros.motor.transfer;

public enum TransferStatus {
    /** Pix para outro banco aguardando o SPI. O valor fica reservado, sem lançamento. */
    PENDING,
    COMPLETED,
    REJECTED
}

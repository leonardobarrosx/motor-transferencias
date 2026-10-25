package dev.leonardobarros.motor.transfer;

public enum TransferKind {
    /** Pix enviado pelo cliente. */
    PIX,
    /** Devolução de um Pix recebido, no sentido contrário. */
    REFUND
}

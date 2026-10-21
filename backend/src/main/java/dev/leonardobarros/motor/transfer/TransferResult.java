package dev.leonardobarros.motor.transfer;

/** @param replayed {@code true} quando a resposta é a repetição de um pedido já processado. */
public record TransferResult(TransferView transfer, boolean replayed) {
}

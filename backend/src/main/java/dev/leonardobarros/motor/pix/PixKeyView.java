package dev.leonardobarros.motor.pix;

/**
 * O que um banco mostra antes de confirmar o Pix: para quem vai o dinheiro.
 * Documento mascarado e número da conta parcial, nunca os dados completos.
 */
public record PixKeyView(
        String pixKey,
        String holderName,
        String document,
        String institution,
        String branch,
        String accountNumber,
        boolean acceptsTransfers) {
}

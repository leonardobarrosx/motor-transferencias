package dev.leonardobarros.motor.pix;

/** Chave Pix de um cliente de outra instituição, como o DICT a devolve. */
public record ChaveExterna(String pixKey, String holderName, String document, String ispb, String institution,
                           String branch, String accountNumber) {
}

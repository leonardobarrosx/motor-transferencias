package dev.leonardobarros.motor.transfer;

/**
 * Recusas de negócio. Ficam gravadas junto com a Idempotency-Key: repetir uma
 * transferência recusada devolve a mesma recusa, mesmo que o saldo tenha mudado.
 */
public enum RejectionReason {

    INSUFFICIENT_FUNDS("Saldo insuficiente para esta transferência."),
    SOURCE_ACCOUNT_BLOCKED("A conta de origem está bloqueada."),
    TARGET_ACCOUNT_BLOCKED("A conta de destino não pode receber transferências.");

    private final String message;

    RejectionReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}

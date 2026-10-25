package dev.leonardobarros.motor.transfer;

import org.springframework.http.HttpStatus;

import dev.leonardobarros.motor.shared.error.DomainException;

/** Pedido de devolução que não pode nem ser avaliado (por isso não é gravado). */
public class RefundNotAllowedException extends DomainException {

    private RefundNotAllowedException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }

    public static RefundNotAllowedException notRefundable() {
        return new RefundNotAllowedException("REFUND_NOT_ALLOWED",
                "Só um Pix recebido e concluído pode ser devolvido.");
    }

    public static RefundNotAllowedException expired() {
        return new RefundNotAllowedException("REFUND_WINDOW_EXPIRED",
                "O prazo de 90 dias para devolver este Pix terminou.");
    }

    public static RefundNotAllowedException exceedsOriginal() {
        return new RefundNotAllowedException("REFUND_AMOUNT_EXCEEDED",
                "A soma das devoluções não pode passar do valor do Pix original.");
    }
}

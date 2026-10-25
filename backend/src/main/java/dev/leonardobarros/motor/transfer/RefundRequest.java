package dev.leonardobarros.motor.transfer;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record RefundRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        RefundReason reason) {

    RefundCommand toCommand(UUID requester, UUID originalTransferId) {
        return new RefundCommand(requester, originalTransferId, amount, reason);
    }
}

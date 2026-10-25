package dev.leonardobarros.motor.transfer;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A conta de origem não vem no corpo: é sempre a de quem está logado. */
public record TransferRequest(
        @NotBlank @Size(max = 77) String targetPixKey,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Size(max = 140) String description) {

    TransferCommand toCommand(UUID sourceAccountId) {
        return new TransferCommand(sourceAccountId, targetPixKey, amount, description);
    }
}

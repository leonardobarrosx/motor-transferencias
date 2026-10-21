package dev.leonardobarros.motor.transfer;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransferRequest(
        @NotNull UUID sourceAccountId,
        @NotBlank @Size(max = 77) String targetPixKey,
        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Size(max = 140) String description) {

    TransferCommand toCommand() {
        return new TransferCommand(sourceAccountId, targetPixKey, amount, description);
    }
}

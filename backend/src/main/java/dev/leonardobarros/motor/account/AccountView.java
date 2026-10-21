package dev.leonardobarros.motor.account;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

public record AccountView(
        UUID id,
        String holderName,
        String document,
        String branch,
        String number,
        String pixKey,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance,
        AccountStatus status,
        MonthToDate monthToDate) {

    /** Movimento do mês corrente (fuso de Brasília), para o painel da conta. */
    public record MonthToDate(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal inflow,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outflow,
            long transfers) {
    }

    /** CPF/CNPJ mascarado: a API nunca devolve o documento inteiro. */
    static String mask(String document) {
        return switch (document.length()) {
            case 11 -> "***.%s.%s-**".formatted(document.substring(3, 6), document.substring(6, 9));
            case 14 -> "**.%s.%s/%s-**".formatted(document.substring(2, 5), document.substring(5, 8), document.substring(8, 12));
            default -> "*".repeat(document.length());
        };
    }
}

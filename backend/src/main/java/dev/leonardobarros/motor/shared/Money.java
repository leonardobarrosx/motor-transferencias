package dev.leonardobarros.motor.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Regras de valor monetário em reais: BigDecimal, sempre com 2 casas e nunca
 * arredondado em silêncio. Um valor com 3 casas é erro do cliente, não do motor.
 */
public final class Money {

    public static final int SCALE = 2;

    private Money() {
    }

    public static BigDecimal requireValid(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Valor monetário obrigatório");
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Valor monetário não pode ser negativo");
        }
        return normalize(value);
    }

    public static BigDecimal requirePositive(BigDecimal value) {
        BigDecimal normalized = requireValid(value);
        if (normalized.signum() == 0) {
            throw new IllegalArgumentException("Valor monetário deve ser maior que zero");
        }
        return normalized;
    }

    /** Lança {@link ArithmeticException} se o valor tiver mais de 2 casas significativas. */
    public static BigDecimal normalize(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }
}

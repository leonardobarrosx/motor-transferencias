package dev.leonardobarros.motor.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import dev.leonardobarros.motor.support.Accounts;

class AccountTest {

    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");

    @Test
    void debitAndCreditMoveTheBalance() {
        Account account = Accounts.active("100.00");

        account.debit(new BigDecimal("30.50"), NOW);
        account.credit(new BigDecimal("10"), NOW);

        assertThat(account.getBalance()).isEqualByComparingTo("79.50");
    }

    @Test
    void debitCanEmptyTheAccountButNeverGoBelowZero() {
        Account account = Accounts.active("50.00");

        account.debit(new BigDecimal("50.00"), NOW);

        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> account.debit(new BigDecimal("0.01"), NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void blockedAccountDoesNotMoveMoney() {
        Account account = Accounts.blocked("100.00");

        assertThatThrownBy(() -> account.debit(BigDecimal.ONE, NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> account.credit(BigDecimal.ONE, NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsZeroNegativeAndFractionsOfCents() {
        Account account = Accounts.active("100.00");

        assertThatThrownBy(() -> account.debit(BigDecimal.ZERO, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(new BigDecimal("-1"), NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.credit(new BigDecimal("0.001"), NOW)).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void masksCpfAndCnpj() {
        assertThat(AccountView.mask("11144477735")).isEqualTo("***.444.777-**");
        assertThat(AccountView.mask("12345678000195")).isEqualTo("**.345.678/0001-**");
    }
}

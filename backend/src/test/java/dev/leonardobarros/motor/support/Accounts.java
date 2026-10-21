package dev.leonardobarros.motor.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.account.AccountStatus;

/** Fábrica de contas para os testes. */
public final class Accounts {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private Accounts() {
    }

    public static Account active(String balance) {
        return with(UUID.randomUUID(), balance, AccountStatus.ACTIVE);
    }

    public static Account blocked(String balance) {
        return with(UUID.randomUUID(), balance, AccountStatus.BLOCKED);
    }

    public static Account with(UUID id, String balance, AccountStatus status) {
        int n = SEQUENCE.incrementAndGet();
        return new Account(id, "Titular " + n, "%011d".formatted(n), "0001", "%07d".formatted(n),
                "titular" + n + "-" + id + "@teste.dev", new BigDecimal(balance), status, Instant.parse("2026-01-01T00:00:00Z"));
    }
}

package dev.leonardobarros.motor.account;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import dev.leonardobarros.motor.shared.error.DomainException;

public class AccountNotFoundException extends DomainException {

    private final UUID accountId;

    public AccountNotFoundException(UUID accountId) {
        super(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Conta não encontrada.");
        this.accountId = accountId;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("accountId", accountId);
    }
}

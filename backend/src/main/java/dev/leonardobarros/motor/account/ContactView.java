package dev.leonardobarros.motor.account;

import java.time.Instant;

public record ContactView(String holderName, String pixKey, Instant lastTransferAt) {
}

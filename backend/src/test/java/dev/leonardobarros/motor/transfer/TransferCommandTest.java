package dev.leonardobarros.motor.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TransferCommandTest {

    private static final UUID SOURCE = UUID.fromString("0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1001");

    @Test
    void equivalentRequestsShareTheFingerprint() {
        var a = new TransferCommand(SOURCE, "ana@motor.dev", new BigDecimal("100"), "  aluguel ");
        var b = new TransferCommand(SOURCE, " ana@motor.dev ", new BigDecimal("100.00"), "aluguel");

        assertThat(a.fingerprint()).isEqualTo(b.fingerprint()).hasSize(64);
    }

    @Test
    void anyMeaningfulChangeChangesTheFingerprint() {
        var base = new TransferCommand(SOURCE, "ana@motor.dev", new BigDecimal("100.00"), null);

        assertThat(base.fingerprint())
                .isNotEqualTo(new TransferCommand(SOURCE, "ana@motor.dev", new BigDecimal("100.01"), null).fingerprint())
                .isNotEqualTo(new TransferCommand(SOURCE, "carlos@motor.dev", new BigDecimal("100.00"), null).fingerprint())
                .isNotEqualTo(new TransferCommand(SOURCE, "ana@motor.dev", new BigDecimal("100.00"), "pix").fingerprint());
    }

    @Test
    void blankDescriptionIsTheSameAsNone() {
        var blank = new TransferCommand(SOURCE, "ana@motor.dev", BigDecimal.TEN, "   ");

        assertThat(blank.description()).isNull();
    }
}

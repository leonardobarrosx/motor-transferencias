package dev.leonardobarros.motor.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class EndToEndIdTest {

    @Test
    void followsThePixLayout() {
        String id = EndToEndId.generate(Instant.parse("2026-10-21T14:05:59Z"));

        assertThat(id)
                .hasSize(32)
                .startsWith("E" + EndToEndId.SIMULATOR_ISPB + "202610211405")
                .matches("E\\d{8}\\d{12}[A-Z0-9]{11}");
    }

    @Test
    void isUniqueWithinTheSameMinute() {
        Instant now = Instant.parse("2026-10-21T14:05:00Z");

        assertThat(EndToEndId.generate(now)).isNotEqualTo(EndToEndId.generate(now));
    }
}

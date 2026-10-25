package dev.leonardobarros.motor.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PixLimitsTest {

    // Brasília é UTC-3 o ano todo desde 2019 (sem horário de verão)
    @ParameterizedTest(name = "{0} em Brasília → {1}, janela {2} até {3}")
    @CsvSource({
            "2026-10-25T09:00:00Z, DAYTIME,   2026-10-25T09:00:00Z, 2026-10-25T23:00:00Z",   // 06:00 em ponto
            "2026-10-25T22:59:59Z, DAYTIME,   2026-10-25T09:00:00Z, 2026-10-25T23:00:00Z",   // 19:59:59
            "2026-10-25T23:00:00Z, NIGHTTIME, 2026-10-25T23:00:00Z, 2026-10-26T09:00:00Z",   // 20:00 em ponto
            "2026-10-26T02:59:00Z, NIGHTTIME, 2026-10-25T23:00:00Z, 2026-10-26T09:00:00Z",   // 23:59
            "2026-10-26T05:00:00Z, NIGHTTIME, 2026-10-25T23:00:00Z, 2026-10-26T09:00:00Z",   // 02:00: a noite começou ONTEM
            "2026-10-26T08:59:59Z, NIGHTTIME, 2026-10-25T23:00:00Z, 2026-10-26T09:00:00Z",   // 05:59:59
    })
    void janelasDiurnaENoturnaNoHorarioDeBrasilia(Instant now, PixLimits.Period period, Instant start, Instant end) {
        PixLimits.Window window = PixLimits.windowAt(now);

        assertThat(window.period()).isEqualTo(period);
        assertThat(window.start()).isEqualTo(start);
        assertThat(window.end()).isEqualTo(end);
    }
}

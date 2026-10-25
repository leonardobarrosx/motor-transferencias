package dev.leonardobarros.motor.transfer;

import java.time.Instant;
import java.util.UUID;

/**
 * Resposta do SPI a um Pix enviado para outra instituição, como o motor-integracoes
 * a publica em pix.spi.results depois de receber a pacs.002.
 *
 * @param status     ACSC (liquidado) ou RJCT (recusado)
 * @param reasonCode código ISO 20022 da recusa (ex.: AC03 conta inválida), só em RJCT
 */
public record SpiResult(UUID eventId, UUID transferId, String endToEndId, String status, String reasonCode,
                        Instant decidedAt) {

    public static final String SETTLED = "ACSC";
    public static final String REJECTED = "RJCT";

    boolean settled() {
        return SETTLED.equals(status);
    }
}

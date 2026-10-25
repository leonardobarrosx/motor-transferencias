package dev.leonardobarros.integracoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import dev.leonardobarros.integracoes.evento.TransferEvent;
import dev.leonardobarros.integracoes.spi.Pacs004;

/**
 * A pacs.004 (PaymentReturn) é a devolução no SPI. Hoje as devoluções são entre
 * contas daqui e não passam pelo SPI; a mensagem fica pronta para quando o motor
 * receber Pix de outros bancos e precisar devolvê-los.
 */
class Pacs004Test {

    private final Pacs004 pacs004 = new Pacs004(Clock.fixed(Instant.parse("2026-10-25T12:00:05Z"), ZoneOffset.UTC));

    @Test
    void devolucaoLevaOE2eOriginalEOMotivoIso() {
        TransferEvent devolucao = Eventos.devolucao("40.00");

        String xml = pacs004.gerar(devolucao);

        assertThat(xml).contains("urn:iso:std:iso:20022:tech:xsd:pacs.004")
                .contains("<RtrId>" + devolucao.endToEndId() + "</RtrId>")
                .contains("<OrgnlEndToEndId>" + devolucao.originalEndToEndId() + "</OrgnlEndToEndId>")
                .contains("<Cd>MD06</Cd>");
    }
}

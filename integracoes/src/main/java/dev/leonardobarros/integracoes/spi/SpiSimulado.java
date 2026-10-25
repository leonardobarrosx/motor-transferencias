package dev.leonardobarros.integracoes.spi;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.leonardobarros.integracoes.evento.TransferEvent;

/**
 * Faz o papel do SPI e do banco recebedor: recebe a pacs.008 e devolve o status
 * (o que seria a pacs.002). Aceita tudo, menos chaves de contas encerradas, que
 * voltam recusadas com AC03 (conta do recebedor inválida). É o que exercita a
 * compensação no motor.
 */
@Component
public class SpiSimulado {

    /** O que o motor recebe em pix.spi.results (o mesmo formato do SpiResult de lá). */
    public record Resposta(UUID eventId, UUID transferId, String endToEndId, String status, String reasonCode,
                           Instant decidedAt) {
    }

    private final Clock clock;

    SpiSimulado(Clock clock) {
        this.clock = clock;
    }

    public Resposta responder(TransferEvent pedido) {
        boolean recusado = pedido.payee().pixKey() != null && pedido.payee().pixKey().startsWith("encerrada");
        return new Resposta(UUID.randomUUID(), pedido.transferId(), pedido.endToEndId(),
                recusado ? "RJCT" : "ACSC", recusado ? "AC03" : null, clock.instant());
    }
}

package dev.leonardobarros.motor.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Fecha a saga do Pix para outro banco: lê as respostas do SPI que o
 * motor-integracoes publica e conclui ou recusa a transferência pendente.
 * Liga junto com o relay do outbox (os dois dependem do Kafka).
 */
@Component
class SpiResultListener {

    private static final Logger log = LoggerFactory.getLogger(SpiResultListener.class);

    private final TransferProcessor processor;
    private final TransferMetrics metrics;
    private final JsonMapper json;

    SpiResultListener(TransferProcessor processor, TransferMetrics metrics, JsonMapper json) {
        this.processor = processor;
        this.metrics = metrics;
        this.json = json;
    }

    @KafkaListener(topics = "${motor.spi.results-topic:pix.spi.results}", groupId = "motor-transferencias",
            autoStartup = "${motor.outbox.relay.enabled:false}")
    void onResult(String message) {
        SpiResult result = json.readValue(message, SpiResult.class);
        boolean applied = processor.applySpiResult(result);
        metrics.spiResult(result.status(), applied);
        log.info("SPI {} para {}{}", result.status(), result.endToEndId(), applied ? "" : " (repetida; ignorada)");
    }
}

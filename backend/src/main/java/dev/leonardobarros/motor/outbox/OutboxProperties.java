package dev.leonardobarros.motor.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * O relay em si é ligado por motor.outbox.relay.enabled. Desligado, os eventos
 * ficam guardados no outbox, e a API roda sem Kafka (como no deploy gratuito).
 */
@ConfigurationProperties(prefix = "motor.outbox")
public record OutboxProperties(String topic, int batchSize, int partitions) {

    public OutboxProperties {
        topic = topic == null ? "pix.transfers" : topic;
        batchSize = batchSize <= 0 ? 100 : batchSize;
        partitions = partitions <= 0 ? 3 : partitions;
    }
}

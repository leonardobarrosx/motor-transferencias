package dev.leonardobarros.motor.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lê o outbox e publica no Kafka. Entrega pelo menos uma vez: se o Kafka confirmar
 * e o commit falhar logo depois, o evento sai de novo no próximo ciclo. Por isso
 * o consumidor deduplica pelo eventId (idempotent consumer, no motor-integracoes).
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;
    private final Clock clock;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka,
                       OutboxProperties properties, Clock clock) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${motor.outbox.relay.interval:1s}")
    @Transactional
    public int publishPending() {
        List<OutboxEvent> batch = outbox.lockNextBatch(properties.batchSize());
        for (OutboxEvent event : batch) {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(properties.topic(), event.getPartitionKey(), event.getPayload());
            record.headers().add("eventType", event.getEventType().getBytes(UTF_8));
            record.headers().add("eventId", event.getId().toString().getBytes(UTF_8));
            if (event.getTraceParent() != null) {
                // o consumidor continua o trace da requisição que criou o Pix
                record.headers().add("traceparent", event.getTraceParent().getBytes(UTF_8));
            }
            try {
                // espera o ack (acks=all): só marca como publicado o que o Kafka confirmou
                kafka.send(record).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // o rollback devolve o lote inteiro para a fila; o próximo ciclo tenta de novo
                log.warn("Kafka indisponível; {} eventos voltam para o outbox", batch.size(), e);
                throw new IllegalStateException("Falha ao publicar evento " + event.getId(), e);
            }
            event.markPublished(clock.instant());
        }
        if (!batch.isEmpty()) {
            log.info("Outbox: {} eventos publicados em {}", batch.size(), properties.topic());
        }
        return batch.size();
    }
}

package dev.leonardobarros.motor.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxProperties.class)
class OutboxConfig {

    /**
     * Saúde do outbox no Prometheus: quantos eventos esperam publicação e há quanto
     * tempo o mais antigo espera. Atraso crescente = Kafka fora ou relay parado,
     * e as notificações dos clientes estão atrasando.
     */
    @Bean
    MeterBinder outboxMetrics(OutboxRepository outbox, Clock clock) {
        return registry -> {
            Gauge.builder("outbox.pendentes", outbox, OutboxRepository::countByPublishedAtIsNull)
                    .description("Eventos gravados e ainda não publicados no Kafka")
                    .register(registry);
            Gauge.builder("outbox.atraso", outbox, repository -> {
                        Instant oldest = repository.oldestPendingCreatedAt();
                        return oldest == null ? 0 : Duration.between(oldest, clock.instant()).toMillis() / 1000.0;
                    })
                    .baseUnit("seconds")
                    .description("Idade do evento pendente mais antigo")
                    .register(registry);
        };
    }

    /** Só existe com motor.outbox.relay.enabled=true: sem Kafka, a API sobe e o outbox acumula. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "motor.outbox.relay.enabled", havingValue = "true")
    static class Relay {

        @Bean
        OutboxRelay outboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka,
                                OutboxProperties properties, Clock clock) {
            return new OutboxRelay(outbox, kafka, properties, clock);
        }

        @Bean
        NewTopic transfersTopic(OutboxProperties properties) {
            return TopicBuilder.name(properties.topic()).partitions(properties.partitions()).replicas(1).build();
        }
    }
}

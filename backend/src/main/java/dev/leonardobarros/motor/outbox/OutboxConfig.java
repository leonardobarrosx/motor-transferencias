package dev.leonardobarros.motor.outbox;

import java.time.Clock;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OutboxProperties.class)
class OutboxConfig {

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

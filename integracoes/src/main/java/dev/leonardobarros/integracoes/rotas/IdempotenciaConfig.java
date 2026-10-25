package dev.leonardobarros.integracoes.rotas;

import java.time.Clock;

import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.processor.aggregate.DefaultAggregateController;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class IdempotenciaConfig {

    /**
     * Guarda os últimos 10 mil eventIds em memória (LRU). Em produção, com mais de
     * uma instância, seria um repositório compartilhado: JDBC ou o próprio Kafka
     * (KafkaIdempotentRepository).
     */
    @Bean
    IdempotentRepository eventosProcessados() {
        return MemoryIdempotentRepository.memoryIdempotentRepository(10_000);
    }

    /** Permite fechar o lote de conciliação na hora (fim do dia, desligamento, testes). */
    @Bean
    AggregateController conciliacaoController() {
        return new DefaultAggregateController();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

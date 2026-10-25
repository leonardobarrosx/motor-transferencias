package dev.leonardobarros.integracoes.rotas;

import java.time.Clock;

import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.processor.aggregate.DefaultAggregateController;
import org.apache.camel.spi.IdempotentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
class IdempotenciaConfig {

    /**
     * eventIds já processados, no banco do serviço: valem entre restarts e entre
     * instâncias. A alternativa sem banco seria o KafkaIdempotentRepository.
     */
    @Bean
    IdempotentRepository eventosProcessados(JdbcTemplate jdbc) {
        return new JdbcIdempotentRepository(jdbc);
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

package dev.leonardobarros.integracoes;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import dev.leonardobarros.integracoes.evento.TransferEvent;

/**
 * O serviço inteiro contra um Kafka de verdade: o consumidor de pix.transfers, as
 * rotas, o SPI simulado e os produtores de pix.spi.results e pix.notifications.
 * Os outros testes trocam o Kafka por mocks; este prova a fiação.
 */
@Testcontainers
@SpringBootTest(properties = {
        "camel.springboot.main-run-controller=false",
        "integracoes.latencia-spi=0ms",
        "integracoes.diretorio-spi=target/it/spi",
        "integracoes.diretorio-conciliacao=target/it/conciliacao"})
@Import(PostgresContainerConfig.class)
class KafkaPontaAPontaIT {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("camel.component.kafka.brokers", KAFKA::getBootstrapServers);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void pixParaOutroBancoVoltaComARespostaDoSpiEOTrace() {
        TransferEvent evento = Eventos.paraOutroBanco("80.00", "maria@bancoexemplo.dev");
        String traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        publicar(evento, traceParent);

        ConsumerRecord<String, String> resposta = consumirAte("pix.spi.results",
                r -> r.value().contains(evento.transferId().toString()));
        assertThat(resposta.key()).isEqualTo(evento.payer().accountId().toString());
        assertThat(resposta.value()).contains("\"status\":\"ACSC\"").contains(evento.endToEndId());
        assertThat(new String(resposta.headers().lastHeader("traceparent").value(), UTF_8)).isEqualTo(traceParent);
        assertThat(jdbc.queryForObject("SELECT tipo FROM mensagens_spi WHERE end_to_end_id = ?", String.class,
                evento.endToEndId())).isEqualTo("pacs.008");
    }

    @Test
    void pixEntreContasDaquiViraDuasNotificacoesNoTopico() {
        TransferEvent evento = Eventos.concluido("12.00");

        publicar(evento, null);

        ConsumerRecord<String, String> paraQuemRecebeu = consumirAte("pix.notifications",
                r -> r.value().contains(evento.transferId().toString()) && r.value().contains("Pix recebido"));
        assertThat(paraQuemRecebeu.key()).isEqualTo(evento.payee().accountId().toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacoes WHERE transfer_id = ?", Long.class,
                evento.transferId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagens_spi WHERE end_to_end_id = ?", Long.class,
                evento.endToEndId())).isZero();
    }

    private static void publicar(TransferEvent evento, String traceParent) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
            var record = new ProducerRecord<>("pix.transfers", evento.payer().accountId().toString(), Eventos.json(evento));
            record.headers().add("eventType", evento.eventType().getBytes(UTF_8));
            if (traceParent != null) {
                record.headers().add("traceparent", traceParent.getBytes(UTF_8));
            }
            producer.send(record);
        }
    }

    private static ConsumerRecord<String, String> consumirAte(String topico, Predicate<ConsumerRecord<String, String>> procurado) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> vistos = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topico));
            long limite = System.currentTimeMillis() + 60_000;
            while (vistos.stream().noneMatch(procurado) && System.currentTimeMillis() < limite) {
                consumer.poll(Duration.ofMillis(500)).forEach(vistos::add);
            }
        }
        return vistos.stream().filter(procurado).findFirst()
                .orElseThrow(() -> new AssertionError("nada em " + topico + " depois de 60 s; vistos: " + vistos.size()));
    }
}

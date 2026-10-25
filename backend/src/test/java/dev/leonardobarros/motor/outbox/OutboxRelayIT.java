package dev.leonardobarros.motor.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;

import dev.leonardobarros.motor.account.Account;
import dev.leonardobarros.motor.account.AccountRepository;
import dev.leonardobarros.motor.account.PixKeyNotFoundException;
import dev.leonardobarros.motor.account.SettlementAccount;
import dev.leonardobarros.motor.support.Accounts;
import dev.leonardobarros.motor.support.PostgresContainerConfig;
import dev.leonardobarros.motor.transfer.TransferCommand;
import dev.leonardobarros.motor.transfer.SpiResult;
import dev.leonardobarros.motor.transfer.TransferService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Transferência → outbox → relay → Kafka real, com Postgres real. */
@SpringBootTest(properties = {"motor.outbox.relay.enabled=true", "motor.outbox.relay.interval=200ms"})
@Import({PostgresContainerConfig.class, OutboxRelayIT.Kafka.class})
class OutboxRelayIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class Kafka {
        @Bean
        @ServiceConnection
        KafkaContainer kafka() {
            return new KafkaContainer("apache/kafka:4.1.0");
        }
    }

    @Autowired TransferService transfers;
    @Autowired AccountRepository accounts;
    @Autowired OutboxRepository outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired KafkaContainer kafka;
    @Autowired Tracer tracer;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE outbox_events, ledger_entries, transfers, users, accounts");
    }

    @Test
    void completedAndRejectedTransfersArePublishedWithThePayerAsKey() throws Exception {
        Account payer = accounts.save(Accounts.active("100.00"));
        Account payee = accounts.save(Accounts.active("0.00"));

        var ok = transfers.execute(UUID.randomUUID(),
                new TransferCommand(payer.getId(), payee.getPixKey(), new BigDecimal("30.00"), "pix"));
        var refused = transfers.execute(UUID.randomUUID(),
                new TransferCommand(payer.getId(), payee.getPixKey(), new BigDecimal("999.00"), null));

        List<ConsumerRecord<String, String>> records = consumeOf(payer.getId().toString(), 2);

        assertThat(records).extracting(ConsumerRecord::key).containsOnly(payer.getId().toString());
        JsonNode completed = json.readTree(records.get(0).value());
        JsonNode rejected = json.readTree(records.get(1).value());
        assertThat(completed.get("eventType").asString()).isEqualTo(TransferEvent.COMPLETED);
        assertThat(completed.get("transferId").asString()).isEqualTo(ok.transfer().id().toString());
        assertThat(completed.get("amount").asString()).isEqualTo("30.00");
        assertThat(completed.get("payee").get("holderName").asString()).isEqualTo(payee.getHolderName());
        assertThat(rejected.get("eventType").asString()).isEqualTo(TransferEvent.REJECTED);
        assertThat(rejected.get("rejectionReason").asString()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(rejected.get("transferId").asString()).isEqualTo(refused.transfer().id().toString());
        assertThat(new String(records.get(0).headers().lastHeader("eventType").value()))
                .isEqualTo(TransferEvent.COMPLETED);

        assertThat(outbox.countByPublishedAtIsNull()).isZero();
    }

    @Test
    void theTraceOfTheRequestTravelsWithTheEvent() {
        Account payer = accounts.save(Accounts.active("100.00"));
        Account payee = accounts.save(Accounts.active("0.00"));

        // como se fosse a requisição HTTP: um span ativo enquanto o Pix é processado
        Span request = tracer.nextSpan().name("POST /api/v1/transfers").start();
        String transferId;
        try (var scope = tracer.withSpan(request)) {
            transferId = transfers.execute(UUID.randomUUID(),
                    new TransferCommand(payer.getId(), payee.getPixKey(), new BigDecimal("5.00"), null))
                    .transfer().id().toString();
        } finally {
            request.end();
        }

        // o relay publica depois, em outra thread; mesmo assim o header continua o mesmo trace
        ConsumerRecord<String, String> record = consumeUntil(r -> r.value().contains(transferId));
        String traceParent = new String(record.headers().lastHeader("traceparent").value());
        assertThat(traceParent).startsWith("00-" + request.context().traceId() + "-");
    }

    @Test
    void externalPixClosesTheSagaThroughKafka() throws Exception {
        jdbc.update("""
                INSERT INTO accounts (id, holder_name, document, branch, number, pix_key, balance, status, type,
                                      version, created_at, updated_at)
                VALUES (?, 'Liquidação Pix', '99999999000000', '0000', 'PI-99999999', 'interno:liquidacao-spi', 0,
                        'ACTIVE', 'SETTLEMENT', 0, now(), now())
                """, SettlementAccount.ID);
        Account payer = accounts.save(Accounts.active("100.00"));
        var pending = transfers.execute(UUID.randomUUID(),
                new TransferCommand(payer.getId(), "maria@bancoexemplo.dev", new BigDecimal("25.00"), null)).transfer();

        // 1. o pedido sai para o motor-integracoes
        ConsumerRecord<String, String> submitted = consumeUntil(r -> r.value().contains(pending.id().toString()));
        assertThat(new String(submitted.headers().lastHeader("eventType").value())).isEqualTo(TransferEvent.SUBMITTED);

        // 2. a resposta do SPI chega pelo Kafka, como o motor-integracoes a publica
        kafkaTemplate.send("pix.spi.results", payer.getId().toString(), json.writeValueAsString(new SpiResult(
                UUID.randomUUID(), pending.id(), pending.endToEndId(), SpiResult.SETTLED, null, Instant.now()))).get();

        // 3. o listener conclui o Pix
        long deadline = System.currentTimeMillis() + 20_000;
        String status = "PENDING";
        while (!"COMPLETED".equals(status) && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            status = jdbc.queryForObject("SELECT status FROM transfers WHERE id = ?", String.class, pending.id());
        }
        assertThat(status).isEqualTo("COMPLETED");
        assertThat(accounts.findById(payer.getId()).orElseThrow().getBalance()).isEqualByComparingTo("75.00");
    }

    @Test
    void aTransferThatFailsLeavesNoEvent() {
        Account payer = accounts.save(Accounts.active("100.00"));

        assertThatThrownBy(() -> transfers.execute(UUID.randomUUID(),
                new TransferCommand(payer.getId(), "ninguem@motor.dev", BigDecimal.TEN, null)))
                .isInstanceOf(PixKeyNotFoundException.class);

        assertThat(outbox.count()).isZero();
    }

    /** Os registros de uma chave (conta pagadora), na ordem da partição. */
    private List<ConsumerRecord<String, String>> consumeOf(String key, int expected) {
        Predicate<ConsumerRecord<String, String>> mine = r -> key.equals(r.key());
        List<ConsumerRecord<String, String>> all = consume(Integer.MAX_VALUE,
                r -> false, records -> records.stream().filter(mine).count() >= expected);
        return all.stream().filter(mine).toList();
    }

    /** O tópico é compartilhado pelos testes da classe: procura o registro deste teste. */
    private ConsumerRecord<String, String> consumeUntil(Predicate<ConsumerRecord<String, String>> match) {
        return consume(Integer.MAX_VALUE, match, records -> false).stream().filter(match).findFirst().orElseThrow();
    }

    private List<ConsumerRecord<String, String>> consume(int expected,
                                                         Predicate<ConsumerRecord<String, String>> stop,
                                                         Predicate<List<ConsumerRecord<String, String>>> enough) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of("pix.transfers"));
            long deadline = System.currentTimeMillis() + 30_000;
            while (records.size() < expected && records.stream().noneMatch(stop) && !enough.test(records)
                    && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
        }
        // mesma chave → mesma partição → ordem de publicação preservada
        return records;
    }
}

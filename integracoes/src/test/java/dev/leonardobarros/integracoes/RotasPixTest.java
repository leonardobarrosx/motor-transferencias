package dev.leonardobarros.integracoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit6.CamelSpringBootTest;
import org.apache.camel.test.spring.junit6.MockEndpointsAndSkip;
import org.apache.camel.test.spring.junit6.UseAdviceWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.leonardobarros.integracoes.evento.TransferEvent;
import dev.leonardobarros.integracoes.notificacao.NotificacoesRecentes;
import dev.leonardobarros.integracoes.rotas.RotasPix;

/**
 * As rotas de verdade, com o consumidor Kafka trocado por um direct: e os
 * endpoints de saída (Kafka e arquivos) trocados por mocks.
 */
@CamelSpringBootTest
@SpringBootTest(properties = {
        "integracoes.intervalo-tentativas=10ms",
        "integracoes.lote-conciliacao=2",
        "integracoes.janela-conciliacao=60s",
        "camel.springboot.main-run-controller=false"})
@UseAdviceWith
@MockEndpointsAndSkip("(kafka|file):.*")
@Import(PostgresContainerConfig.class)
class RotasPixTest {

    private static final String ENTRADA = "direct:entrada";

    @Autowired CamelContext camel;
    @Autowired ProducerTemplate producer;
    @Autowired NotificacoesRecentes recentes;
    @Autowired JdbcTemplate jdbc;
    @Autowired AggregateController conciliacaoController;

    @EndpointInject("mock:kafka:pix.notifications") MockEndpoint notificacoes;
    @EndpointInject("mock:file:data/spi/saida") MockEndpoint spi;
    @EndpointInject("mock:file:data/conciliacao") MockEndpoint conciliacao;
    @EndpointInject("mock:kafka:pix.transfers.dlq") MockEndpoint dlq;

    @BeforeEach
    void iniciar() throws Exception {
        if (!camel.isStarted()) {
            AdviceWith.adviceWith(camel, RotasPix.ENTRADA, rota -> rota.replaceFromWith(ENTRADA));
            camel.start();
        }
        // o agregador guarda estado entre testes: fecha qualquer lote pendente antes de começar
        if (conciliacaoController.forceCompletionOfAllGroups() > 0) {
            Thread.sleep(200);
        }
        MockEndpoint.resetMocks(camel);
    }

    @Test
    void pixConcluidoGeraNotificacoesEPacs008() throws Exception {
        TransferEvent evento = Eventos.concluido("150.75");
        notificacoes.expectedMessageCount(2);
        spi.expectedMessageCount(1);
        spi.expectedHeaderReceived(Exchange.FILE_NAME, evento.endToEndId() + ".xml");

        producer.sendBody(ENTRADA, Eventos.json(evento));

        MockEndpoint.assertIsSatisfied(camel, 5, TimeUnit.SECONDS);
        assertThat(spi.getReceivedExchanges().getFirst().getMessage().getBody(String.class))
                .contains("<EndToEndId>" + evento.endToEndId() + "</EndToEndId>");
        assertThat(notificacoes.getReceivedExchanges().getFirst().getMessage().getHeader("kafka.KEY"))
                .hasToString(evento.payer().accountId().toString());
    }

    @Test
    void eventoRepetidoEhProcessadoUmaVezSo() throws Exception {
        String mesmoEvento = Eventos.json(Eventos.concluido("10.00"));
        spi.expectedMessageCount(1);
        notificacoes.expectedMessageCount(2);

        producer.sendBody(ENTRADA, mesmoEvento);
        producer.sendBody(ENTRADA, mesmoEvento);   // o outbox entrega pelo menos uma vez

        MockEndpoint.assertIsSatisfied(camel, 5, TimeUnit.SECONDS);
    }

    @Test
    void deduplicacaoEstaNoBancoESobreviveARestart() throws Exception {
        TransferEvent evento = Eventos.concluido("12.00");
        notificacoes.expectedMessageCount(2);

        producer.sendBody(ENTRADA, Eventos.json(evento));
        MockEndpoint.assertIsSatisfied(camel, 5, TimeUnit.SECONDS);

        // o eventId está no Postgres, não na memória da instância
        assertThat(jdbc.queryForObject("SELECT count(*) FROM eventos_processados WHERE event_id = ?",
                Long.class, evento.eventId().toString())).isEqualTo(1);
        // e a gravação da notificação é idempotente por si só (UNIQUE transfer + conta)
        recentes.registrar(recentes.daConta(evento.payer().accountId()).getFirst());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacoes WHERE transfer_id = ?",
                Long.class, evento.transferId())).isEqualTo(2);
    }

    @Test
    void pixRecusadoSoNotificaQuemPagou() throws Exception {
        notificacoes.expectedMessageCount(1);
        spi.expectedMessageCount(0);

        TransferEvent recusado = Eventos.recusado("999.00");
        producer.sendBody(ENTRADA, Eventos.json(recusado));

        MockEndpoint.assertIsSatisfied(camel, 5, TimeUnit.SECONDS);
        assertThat(recentes.daConta(recusado.payer().accountId())).extracting("titulo").containsExactly("Pix não realizado");
        assertThat(recentes.daConta(recusado.payee().accountId())).isEmpty();
    }

    @Test
    void conciliacaoFechaOLoteNumUnicoCsv() throws Exception {
        TransferEvent a = Eventos.concluido("1.00");
        TransferEvent b = Eventos.concluido("2.50");
        conciliacao.expectedMessageCount(1);

        producer.sendBody(ENTRADA, Eventos.json(a));
        producer.sendBody(ENTRADA, Eventos.json(b));

        MockEndpoint.assertIsSatisfied(camel, 5, TimeUnit.SECONDS);
        String csv = conciliacao.getReceivedExchanges().getFirst().getMessage().getBody(String.class);
        assertThat(csv.lines()).hasSize(3).first().asString().startsWith("end_to_end_id;");
        assertThat(csv).contains(a.endToEndId()).contains(b.endToEndId());
    }

    @Test
    void devolucaoViraPacs004ComOE2eDoPixOriginal() throws Exception {
        TransferEvent devolucao = Eventos.devolucao("40.00");
        spi.expectedMessageCount(1);
        notificacoes.expectedMessageCount(2);

        producer.sendBody(ENTRADA, Eventos.json(devolucao));

        MockEndpoint.assertIsSatisfied(camel, 5, TimeUnit.SECONDS);
        String xml = spi.getReceivedExchanges().getFirst().getMessage().getBody(String.class);
        assertThat(xml).contains("pacs.004").contains("<RtrId>" + devolucao.endToEndId() + "</RtrId>")
                .contains("<OrgnlEndToEndId>" + devolucao.originalEndToEndId() + "</OrgnlEndToEndId>")
                .contains("<Cd>MD06</Cd>");
        assertThat(recentes.daConta(devolucao.payer().accountId())).extracting("titulo").containsExactly("Devolução enviada");
        assertThat(recentes.daConta(devolucao.payee().accountId())).extracting("titulo").containsExactly("Devolução recebida");
    }

    @Test
    void mensagemEnvenenadaVaiParaADlqSemTravarOConsumo() throws Exception {
        dlq.expectedBodiesReceived("isto não é json");
        spi.expectedMessageCount(1);

        producer.sendBody(ENTRADA, "isto não é json");
        producer.sendBody(ENTRADA, Eventos.json(Eventos.concluido("5.00")));   // a seguinte passa normalmente

        MockEndpoint.assertIsSatisfied(camel, 5, TimeUnit.SECONDS);
    }
}

package dev.leonardobarros.integracoes.rotas;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.spi.IdempotentRepository;
import org.springframework.stereotype.Component;

import dev.leonardobarros.integracoes.IntegracoesProperties;
import dev.leonardobarros.integracoes.conciliacao.LinhasConciliacao;
import dev.leonardobarros.integracoes.evento.TransferEvent;
import dev.leonardobarros.integracoes.notificacao.ComposicaoNotificacoes;
import dev.leonardobarros.integracoes.notificacao.NotificacoesRecentes;
import dev.leonardobarros.integracoes.spi.MensagensSpi;
import dev.leonardobarros.integracoes.spi.Pacs004;
import dev.leonardobarros.integracoes.spi.Pacs008;

/**
 * As rotas do serviço, cada uma um padrão de integração (EIP):
 *
 * <pre>
 * kafka pix.transfers ─▶ [Idempotent Consumer] ─▶ [Content-Based Router]
 *        TransferCompleted ─▶ [Multicast] ─▶ notificações · SPI (pacs.008) · conciliação
 *        TransferRejected  ─▶ notificações
 * falha depois de N tentativas ─▶ [Dead Letter Channel] kafka pix.transfers.dlq
 * </pre>
 */
@Component
public class RotasPix extends RouteBuilder {

    public static final String ENTRADA = "pix-eventos";
    public static final String LIQUIDADO = "direct:liquidado";
    public static final String NOTIFICAR = "direct:notificar";
    public static final String SPI = "direct:spi";
    public static final String CONCILIAR = "direct:conciliar";

    private final IntegracoesProperties props;
    private final IdempotentRepository eventosProcessados;
    private final ComposicaoNotificacoes composicao;
    private final NotificacoesRecentes recentes;
    private final Pacs008 pacs008;
    private final Pacs004 pacs004;
    private final MensagensSpi mensagensSpi;
    private final AggregateController conciliacaoController;

    // os beans entram pelo construtor: .bean(Classe.class) faria o Camel criar outra instância, fora do Spring
    public RotasPix(IntegracoesProperties props, IdempotentRepository eventosProcessados,
                    ComposicaoNotificacoes composicao, NotificacoesRecentes recentes,
                    Pacs008 pacs008, Pacs004 pacs004, MensagensSpi mensagensSpi,
                    AggregateController conciliacaoController) {
        this.props = props;
        this.eventosProcessados = eventosProcessados;
        this.composicao = composicao;
        this.recentes = recentes;
        this.pacs008 = pacs008;
        this.pacs004 = pacs004;
        this.mensagensSpi = mensagensSpi;
        this.conciliacaoController = conciliacaoController;
    }

    @Override
    public void configure() {
        // Falhou? Tenta de novo com backoff; esgotou, a mensagem ORIGINAL vai para a DLQ e o
        // consumo segue (uma mensagem envenenada não trava a partição inteira).
        errorHandler(deadLetterChannel("kafka:" + props.topicoDlq())
                .maximumRedeliveries(props.tentativas())
                .redeliveryDelay(props.intervaloTentativas().toMillis())
                .useExponentialBackOff()
                .useOriginalMessage()
                .logExhaustedMessageHistory(false)
                .retryAttemptedLogLevel(LoggingLevel.WARN));

        from("kafka:" + props.topicoTransferencias() + "?groupId=" + props.grupoConsumidor() + "&autoOffsetReset=earliest")
                .routeId(ENTRADA)
                .unmarshal().json(JsonLibrary.Jackson, TransferEvent.class)
                // O outbox entrega pelo menos uma vez: o mesmo eventId pode chegar de novo
                .idempotentConsumer(simple("${body.eventId}"), eventosProcessados)
                    .skipDuplicate(false)
                .filter(exchangeProperty(Exchange.DUPLICATE_MESSAGE).isEqualTo(true))
                    .log(LoggingLevel.INFO, "Evento ${body.eventId} já processado; ignorado")
                    .stop()
                .end()
                .log("Evento ${body.eventType} · ${body.endToEndId} · R$ ${body.amount}")
                .choice()
                    .when(simple("${body.eventType} == '" + TransferEvent.COMPLETED + "'"))
                        .to(LIQUIDADO)
                    .when(simple("${body.eventType} == '" + TransferEvent.REJECTED + "'"))
                        .to(NOTIFICAR)
                    .otherwise()
                        .log(LoggingLevel.WARN, "Tipo de evento desconhecido: ${body.eventType}")
                .end();

        // um Pix liquidado interessa a três consumidores, cada um com sua própria rota
        from(LIQUIDADO).routeId("pix-liquidado")
                .multicast().to(NOTIFICAR, SPI, CONCILIAR);

        from(NOTIFICAR).routeId("notificacoes")
                .bean(composicao, "compor")
                .split(body())
                    .bean(recentes, "registrar(${body})")
                    .setHeader("kafka.KEY", simple("${body.accountId}"))
                    .marshal().json(JsonLibrary.Jackson)
                    .to("kafka:" + props.topicoNotificacoes())
                .end();

        from(SPI).routeId("spi-pacs008")
                .setHeader(Exchange.FILE_NAME, simple("${body.endToEndId}.xml"))
                .setProperty("evento", body())
                // Pix vira ordem de crédito (pacs.008); devolução vira PaymentReturn (pacs.004)
                .choice()
                    .when(simple("${body.kind} == 'REFUND'"))
                        .bean(pacs004, "gerar")
                    .otherwise()
                        .bean(pacs008, "gerar")
                .end()
                .to("file:" + props.diretorioSpi())
                .bean(mensagensSpi, "registrar(${exchangeProperty.evento}, ${header.CamelFileName})")
                .log("Mensagem ISO 20022 entregue ao SPI: ${header.CamelFileName}");

        from(CONCILIAR).routeId("conciliacao")
                .aggregate(constant("lote"), new LinhasConciliacao())
                    .completionSize(props.loteConciliacao())
                    .completionTimeout(props.janelaConciliacao().toMillis())
                    .aggregateController(conciliacaoController)
                .setHeader(Exchange.FILE_NAME, simple("conciliacao-${date:now:yyyyMMdd-HHmmss-SSS}.csv"))
                .to("file:" + props.diretorioConciliacao())
                .log("Arquivo de conciliação gerado: ${header.CamelFileName}");
    }
}

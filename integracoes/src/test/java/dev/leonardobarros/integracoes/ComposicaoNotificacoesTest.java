package dev.leonardobarros.integracoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import dev.leonardobarros.integracoes.evento.TransferEvent;
import dev.leonardobarros.integracoes.notificacao.ComposicaoNotificacoes;
import dev.leonardobarros.integracoes.notificacao.Notificacao;

class ComposicaoNotificacoesTest {

    private final ComposicaoNotificacoes composicao = new ComposicaoNotificacoes(Clock.systemUTC());

    @Test
    void pixConcluidoAvisaOsDoisLados() {
        TransferEvent evento = Eventos.concluido("1234.50");

        var notificacoes = composicao.compor(evento);

        assertThat(notificacoes).extracting(Notificacao::accountId)
                .containsExactly(evento.payer().accountId(), evento.payee().accountId());
        assertThat(notificacoes.get(0).mensagem()).isEqualTo("Você enviou R$ 1.234,50 para Padaria Sol Nasce.");
        assertThat(notificacoes.get(1).mensagem()).isEqualTo("Você recebeu R$ 1.234,50 de Leonardo Barros.");
    }

    @Test
    void pixRecusadoAvisaSoQuemPagouComOMotivo() {
        TransferEvent evento = Eventos.recusado("99.90");

        var notificacoes = composicao.compor(evento);

        assertThat(notificacoes).singleElement().satisfies(n -> {
            assertThat(n.accountId()).isEqualTo(evento.payer().accountId());
            assertThat(n.mensagem()).endsWith("não foi realizado: saldo insuficiente.");
        });
    }
}

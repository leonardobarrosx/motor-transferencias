package dev.leonardobarros.integracoes.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.leonardobarros.integracoes.notificacao.Notificacao;
import dev.leonardobarros.integracoes.notificacao.NotificacoesRecentes;
import dev.leonardobarros.integracoes.spi.MensagensSpi;

/** Janela para a demonstração: o que o serviço produziu a partir dos eventos. */
@RestController
@RequestMapping("/api")
class ConsultaController {

    private final NotificacoesRecentes notificacoes;
    private final MensagensSpi spi;

    ConsultaController(NotificacoesRecentes notificacoes, MensagensSpi spi) {
        this.notificacoes = notificacoes;
        this.spi = spi;
    }

    @GetMapping("/notificacoes")
    List<Notificacao> notificacoes() {
        return notificacoes.listar();
    }

    @GetMapping("/spi/mensagens")
    List<MensagensSpi.Entregue> mensagensSpi() {
        return spi.listar();
    }
}

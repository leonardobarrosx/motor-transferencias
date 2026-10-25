package dev.leonardobarros.integracoes.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.leonardobarros.integracoes.notificacao.Notificacao;
import dev.leonardobarros.integracoes.notificacao.NotificacoesRecentes;
import dev.leonardobarros.integracoes.spi.MensagensSpi;

/**
 * O que o serviço produziu a partir dos eventos, sempre recortado pela conta do
 * token: a mesma regra anti-IDOR do motor, aplicada no segundo serviço.
 */
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
    List<Notificacao> notificacoes(@AuthenticationPrincipal Jwt token) {
        return notificacoes.daConta(contaDo(token));
    }

    @GetMapping("/spi/mensagens")
    List<MensagensSpi.Entregue> mensagensSpi(@AuthenticationPrincipal Jwt token) {
        return spi.daConta(contaDo(token));
    }

    private static UUID contaDo(Jwt token) {
        return UUID.fromString(token.getClaimAsString("account_id"));
    }
}

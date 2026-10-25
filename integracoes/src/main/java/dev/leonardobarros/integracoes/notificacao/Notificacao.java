package dev.leonardobarros.integracoes.notificacao;

import java.time.Instant;
import java.util.UUID;

/** Mensagem pronta para um canal de envio (push, e-mail, SMS) consumir do tópico. */
public record Notificacao(
        UUID id,
        UUID accountId,
        String destinatario,
        String titulo,
        String mensagem,
        UUID transferId,
        Instant criadaEm) {
}

package dev.leonardobarros.integracoes.notificacao;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.leonardobarros.integracoes.evento.TransferEvent;

/** Transforma um evento de transferência nas notificações de cada lado (Message Translator). */
@Component
public class ComposicaoNotificacoes {

    private final Clock clock;

    public ComposicaoNotificacoes(Clock clock) {
        this.clock = clock;
    }

    public List<Notificacao> compor(TransferEvent evento) {
        String valor = reais(evento.amount());
        if (evento.completed()) {
            return List.of(
                    notificacao(evento, evento.payer(), "Pix enviado",
                            "Você enviou %s para %s.".formatted(valor, evento.payee().holderName())),
                    notificacao(evento, evento.payee(), "Pix recebido",
                            "Você recebeu %s de %s.".formatted(valor, evento.payer().holderName())));
        }
        return List.of(notificacao(evento, evento.payer(), "Pix não realizado",
                "Seu Pix de %s para %s não foi realizado: %s."
                        .formatted(valor, evento.payee().holderName(), motivo(evento.rejectionReason()))));
    }

    private Notificacao notificacao(TransferEvent evento, TransferEvent.Party para, String titulo, String mensagem) {
        return new Notificacao(UUID.randomUUID(), para.accountId(), para.holderName(), titulo, mensagem,
                evento.transferId(), clock.instant());
    }

    static String reais(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor).replace(' ', ' ');
    }

    private static String motivo(String codigo) {
        return switch (codigo == null ? "" : codigo) {
            case "INSUFFICIENT_FUNDS" -> "saldo insuficiente";
            case "SOURCE_ACCOUNT_BLOCKED" -> "sua conta está bloqueada";
            case "TARGET_ACCOUNT_BLOCKED" -> "a conta de destino não pode receber";
            default -> "recusado pelo banco";
        };
    }
}

package dev.leonardobarros.integracoes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.leonardobarros.integracoes.evento.TransferEvent;

/** Eventos de exemplo, no formato que o motor publica. */
public final class Eventos {

    private Eventos() {
    }

    public static TransferEvent concluido(String valor) {
        return evento(TransferEvent.COMPLETED, valor, null);
    }

    public static TransferEvent recusado(String valor) {
        return evento(TransferEvent.REJECTED, valor, "INSUFFICIENT_FUNDS");
    }

    /** Devolução: quem paga agora é a padaria (recebedora do Pix original). */
    public static TransferEvent devolucao(String valor) {
        TransferEvent base = evento(TransferEvent.COMPLETED, valor, null);
        return new TransferEvent(base.eventId(), base.eventType(), 1, base.occurredAt(), base.transferId(),
                "D" + base.endToEndId().substring(1), base.amount(), "BRL", "Devolução de Pix", null,
                "REFUND", UUID.randomUUID(), base.endToEndId(), "MD06", base.payee(), base.payer());
    }

    private static TransferEvent evento(String tipo, String valor, String motivo) {
        UUID transferId = UUID.randomUUID();
        String sufixo = transferId.toString().replace("-", "").substring(0, 11).toUpperCase();
        return new TransferEvent(UUID.randomUUID(), tipo, 1, Instant.parse("2026-10-25T12:00:00Z"), transferId,
                "E99999999202610251200" + sufixo, new BigDecimal(valor), "BRL", "Aluguel <outubro> & cia", motivo,
                "PIX", null, null, null,
                new TransferEvent.Party(UUID.randomUUID(), "Leonardo Barros", "11144477700", "99999999", "0001", "10001-7", "leonardo@motor.dev"),
                new TransferEvent.Party(UUID.randomUUID(), "Padaria Sol Nasce", "12345678000100", "99999999", "0002", "20004-1", "pix@padariasol.dev"));
    }

    public static String json(TransferEvent e) {
        return """
                {"eventId":"%s","eventType":"%s","schemaVersion":1,"occurredAt":"%s","transferId":"%s",
                 "endToEndId":"%s","amount":"%s","currency":"BRL","description":"%s","rejectionReason":%s,
                 "kind":"%s","originalEndToEndId":%s,"refundReasonCode":%s,
                 "campoNovoQueOMotorAcrescentou":"ignorado",
                 "payer":{"accountId":"%s","holderName":"%s","taxId":"%s","ispb":"99999999","branch":"0001","accountNumber":"10001-7","pixKey":"%s"},
                 "payee":{"accountId":"%s","holderName":"%s","taxId":"%s","ispb":"99999999","branch":"0002","accountNumber":"20004-1","pixKey":"%s"}}
                """.formatted(e.eventId(), e.eventType(), e.occurredAt(), e.transferId(), e.endToEndId(),
                e.amount().toPlainString(), e.description().replace("\"", "\\\""),
                e.rejectionReason() == null ? "null" : "\"" + e.rejectionReason() + "\"",
                e.kind(), e.originalEndToEndId() == null ? "null" : "\"" + e.originalEndToEndId() + "\"",
                e.refundReasonCode() == null ? "null" : "\"" + e.refundReasonCode() + "\"",
                e.payer().accountId(), e.payer().holderName(), e.payer().taxId(), e.payer().pixKey(),
                e.payee().accountId(), e.payee().holderName(), e.payee().taxId(), e.payee().pixKey());
    }
}

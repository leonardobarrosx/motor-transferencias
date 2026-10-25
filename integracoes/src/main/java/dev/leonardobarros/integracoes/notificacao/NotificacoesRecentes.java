package dev.leonardobarros.integracoes.notificacao;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * As notificações produzidas, gravadas no banco do serviço para consulta pela API.
 * O destino de envio continua sendo o tópico pix.notifications.
 */
@Component
public class NotificacoesRecentes {

    private static final int LIMITE = 50;

    private final JdbcTemplate jdbc;

    NotificacoesRecentes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Idempotente: a mesma notificação (transferência + conta) só é gravada uma vez. */
    public void registrar(Notificacao n) {
        jdbc.update("""
                INSERT INTO notificacoes (id, account_id, destinatario, titulo, mensagem, transfer_id, criada_em)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT ON CONSTRAINT uk_notificacoes_transfer_conta DO NOTHING
                """, n.id(), n.accountId(), n.destinatario(), n.titulo(), n.mensagem(), n.transferId(),
                Timestamp.from(n.criadaEm()));
    }

    /** Só as da conta: cada cliente vê as próprias notificações. */
    public List<Notificacao> daConta(UUID accountId) {
        return jdbc.query("""
                SELECT id, account_id, destinatario, titulo, mensagem, transfer_id, criada_em
                FROM notificacoes WHERE account_id = ? ORDER BY criada_em DESC, titulo LIMIT ?
                """, (rs, i) -> new Notificacao(
                        rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                        rs.getString("destinatario"), rs.getString("titulo"), rs.getString("mensagem"),
                        rs.getObject("transfer_id", UUID.class), rs.getTimestamp("criada_em").toInstant()),
                accountId, LIMITE);
    }
}

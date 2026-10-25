package dev.leonardobarros.integracoes.spi;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import dev.leonardobarros.integracoes.evento.TransferEvent;

/** Registro das mensagens entregues ao SPI simulado, para consulta pela API. */
@Component
public class MensagensSpi {

    public record Entregue(String endToEndId, String tipo, BigDecimal valor, String arquivo, Instant entregueEm) {
    }

    private static final int LIMITE = 50;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    MensagensSpi(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void registrar(TransferEvent evento, String arquivo) {
        jdbc.update("""
                INSERT INTO mensagens_spi (end_to_end_id, tipo, valor, arquivo, conta_pagadora, conta_recebedora, entregue_em)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (end_to_end_id) DO NOTHING
                """, evento.endToEndId(), evento.refund() ? "pacs.004" : "pacs.008", evento.amount(), arquivo,
                evento.payer().accountId(), evento.payee().accountId(), Timestamp.from(clock.instant()));
    }

    /** As mensagens em que a conta pagou ou recebeu. */
    public List<Entregue> daConta(UUID accountId) {
        return jdbc.query("""
                SELECT end_to_end_id, tipo, valor, arquivo, entregue_em FROM mensagens_spi
                WHERE conta_pagadora = ? OR conta_recebedora = ? ORDER BY entregue_em DESC LIMIT ?
                """, (rs, i) -> new Entregue(rs.getString("end_to_end_id"), rs.getString("tipo"),
                        rs.getBigDecimal("valor"), rs.getString("arquivo"), rs.getTimestamp("entregue_em").toInstant()),
                accountId, accountId, LIMITE);
    }
}

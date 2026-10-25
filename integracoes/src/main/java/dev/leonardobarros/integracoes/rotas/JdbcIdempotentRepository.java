package dev.leonardobarros.integracoes.rotas;

import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Repositório do Idempotent Consumer no Postgres do serviço. O {@code add} é um
 * INSERT ... ON CONFLICT DO NOTHING: com duas instâncias consumindo, só uma ganha
 * o eventId, e a decisão sobrevive a restart.
 *
 * <p>O Camel tem o JdbcMessageIdRepository (camel-sql); este faz o mesmo em vinte
 * linhas sobre o JdbcTemplate que o serviço já usa.
 */
class JdbcIdempotentRepository extends ServiceSupport implements IdempotentRepository {

    private final JdbcTemplate jdbc;

    JdbcIdempotentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean add(String key) {
        return jdbc.update("INSERT INTO eventos_processados (event_id) VALUES (?) ON CONFLICT DO NOTHING", key) == 1;
    }

    @Override
    public boolean contains(String key) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM eventos_processados WHERE event_id = ?)", Boolean.class, key));
    }

    /** Chamado quando o processamento falha: o evento poderá ser tentado de novo. */
    @Override
    public boolean remove(String key) {
        return jdbc.update("DELETE FROM eventos_processados WHERE event_id = ?", key) == 1;
    }

    @Override
    public boolean confirm(String key) {
        return true;
    }

    @Override
    public void clear() {
        jdbc.update("DELETE FROM eventos_processados");
    }
}

package dev.leonardobarros.motor.pix;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * O DICT é o diretório de chaves Pix do Banco Central: diz de quem é uma chave e em
 * qual instituição está a conta. Aqui é uma tabela com chaves fictícias de outros bancos.
 */
@Component
public class DictSimulado {

    private final JdbcTemplate jdbc;

    DictSimulado(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ChaveExterna> consultar(String pixKey) {
        return jdbc.query("""
                SELECT pix_key, holder_name, document, ispb, institution, branch, account_number
                FROM dict_chaves_externas WHERE pix_key = ?
                """, (rs, i) -> new ChaveExterna(rs.getString("pix_key"), rs.getString("holder_name"),
                        rs.getString("document"), rs.getString("ispb"), rs.getString("institution"),
                        rs.getString("branch"), rs.getString("account_number")), pixKey)
                .stream().findFirst();
    }
}

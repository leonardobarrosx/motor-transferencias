package dev.leonardobarros.integracoes.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;

import dev.leonardobarros.integracoes.evento.TransferEvent;

/** Registro das mensagens entregues ao SPI simulado, para consulta pela API. */
@Component
public class MensagensSpi {

    public record Entregue(String endToEndId, BigDecimal valor, String arquivo, Instant entregueEm) {
    }

    private static final int LIMITE = 200;
    private final Deque<Entregue> itens = new ConcurrentLinkedDeque<>();

    public void registrar(TransferEvent evento, String arquivo) {
        itens.addFirst(new Entregue(evento.endToEndId(), evento.amount(), arquivo, Instant.now()));
        while (itens.size() > LIMITE) {
            itens.pollLast();
        }
    }

    public List<Entregue> listar() {
        return new ArrayList<>(itens);
    }
}

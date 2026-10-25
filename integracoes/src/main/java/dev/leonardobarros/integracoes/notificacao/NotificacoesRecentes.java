package dev.leonardobarros.integracoes.notificacao;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;

/**
 * Últimas notificações em memória, só para inspecionar a demonstração pela API.
 * O destino real é o tópico pix.notifications.
 */
@Component
public class NotificacoesRecentes {

    private static final int LIMITE = 200;

    private final Deque<Notificacao> itens = new ConcurrentLinkedDeque<>();

    public void registrar(Notificacao notificacao) {
        itens.addFirst(notificacao);
        while (itens.size() > LIMITE) {
            itens.pollLast();
        }
    }

    public List<Notificacao> listar() {
        return new ArrayList<>(itens);
    }
}

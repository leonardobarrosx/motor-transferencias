package dev.leonardobarros.integracoes.conciliacao;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;

import dev.leonardobarros.integracoes.evento.TransferEvent;

/**
 * Junta os Pix liquidados num único CSV (Aggregator). O lote fecha por tamanho
 * ou por tempo, o que vier primeiro (ver a rota de conciliação).
 */
public class LinhasConciliacao implements AggregationStrategy {

    static final String CABECALHO = "end_to_end_id;transfer_id;valor;ispb_pagador;ispb_recebedor;ocorrido_em\n";

    @Override
    public Exchange aggregate(Exchange acumulado, Exchange novo) {
        TransferEvent evento = novo.getMessage().getBody(TransferEvent.class);
        String linha = String.join(";", evento.endToEndId(), evento.transferId().toString(),
                evento.amount().toPlainString(), evento.payer().ispb(), evento.payee().ispb(),
                evento.occurredAt().toString()) + "\n";
        if (acumulado == null) {
            novo.getMessage().setBody(CABECALHO + linha);
            return novo;
        }
        acumulado.getMessage().setBody(acumulado.getMessage().getBody(String.class) + linha);
        return acumulado;
    }
}

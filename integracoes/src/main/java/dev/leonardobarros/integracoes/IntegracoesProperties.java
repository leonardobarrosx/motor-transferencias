package dev.leonardobarros.integracoes;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param topicoTransferencias  de onde vêm os eventos do motor
 * @param topicoNotificacoes    para onde vão as notificações prontas para envio
 * @param topicoDlq             mensagens que falharam depois de todas as tentativas
 * @param diretorioSpi          onde as mensagens pacs.008 são "entregues" ao SPI simulado
 * @param diretorioConciliacao  arquivos CSV de conciliação
 * @param topicoRespostasSpi    para onde vão as respostas do SPI simulado (o motor fecha o Pix)
 * @param latenciaSpi           quanto o SPI simulado demora para responder
 */
@ConfigurationProperties(prefix = "integracoes")
public record IntegracoesProperties(
        String topicoTransferencias,
        String topicoNotificacoes,
        String topicoDlq,
        String grupoConsumidor,
        String diretorioSpi,
        String diretorioConciliacao,
        int tentativas,
        Duration intervaloTentativas,
        int loteConciliacao,
        Duration janelaConciliacao,
        String topicoRespostasSpi,
        Duration latenciaSpi) {
}

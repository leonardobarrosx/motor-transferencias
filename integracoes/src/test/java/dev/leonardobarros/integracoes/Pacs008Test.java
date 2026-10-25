package dev.leonardobarros.integracoes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import dev.leonardobarros.integracoes.evento.TransferEvent;
import dev.leonardobarros.integracoes.spi.Pacs008;

class Pacs008Test {

    private final Pacs008 pacs008 = new Pacs008(Clock.fixed(Instant.parse("2026-10-25T12:00:05Z"), ZoneOffset.UTC));

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String text(Document doc, String tag, int index) {
        return doc.getElementsByTagNameNS("*", tag).item(index).getTextContent();
    }

    @Test
    void geraUmPacs008BemFormadoComOsDadosDaTransferencia() throws Exception {
        TransferEvent evento = Eventos.concluido("150.75");

        Document doc = parse(pacs008.gerar(evento));

        assertThat(doc.getDocumentElement().getNamespaceURI()).isEqualTo("urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08");
        assertThat(text(doc, "EndToEndId", 0)).isEqualTo(evento.endToEndId());
        assertThat(text(doc, "IntrBkSttlmAmt", 0)).isEqualTo("150.75");
        assertThat(doc.getElementsByTagNameNS("*", "IntrBkSttlmAmt").item(0).getAttributes().getNamedItem("Ccy").getNodeValue())
                .isEqualTo("BRL");
        assertThat(text(doc, "Nm", 0)).isEqualTo("Leonardo Barros");
        assertThat(text(doc, "Nm", 1)).isEqualTo("Padaria Sol Nasce");
        assertThat(text(doc, "MsgId", 0)).startsWith("M9999999920261025120005");
    }

    @Test
    void caracteresEspeciaisSaemEscapados() throws Exception {
        String xml = pacs008.gerar(Eventos.concluido("10.00"));

        assertThat(xml).contains("Aluguel &lt;outubro&gt; &amp; cia");
        assertThat(text(parse(xml), "Ustrd", 0)).isEqualTo("Aluguel <outubro> & cia");
    }

    @Test
    void cnpjViraOrgIdECpfViraPrvtId() throws Exception {
        Document doc = parse(pacs008.gerar(Eventos.concluido("10.00")));

        assertThat(doc.getElementsByTagNameNS("*", "PrvtId").getLength()).isEqualTo(1);   // pagador, CPF
        assertThat(doc.getElementsByTagNameNS("*", "OrgId").getLength()).isEqualTo(1);    // padaria, CNPJ
    }
}

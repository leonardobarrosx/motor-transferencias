package dev.leonardobarros.integracoes.spi;

import java.io.StringWriter;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.springframework.stereotype.Component;

import dev.leonardobarros.integracoes.evento.TransferEvent;

/**
 * Devolução em ISO 20022 pacs.004 (PaymentReturn), simplificada. Leva o
 * identificador da devolução (D…), o E2E do Pix original e o código do motivo.
 */
@Component
public class Pacs004 {

    static final String NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pacs.004.001.09";
    private static final DateTimeFormatter MSG_ID = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final Clock clock;

    public Pacs004(Clock clock) {
        this.clock = clock;
    }

    public String gerar(TransferEvent evento) {
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(out);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("Document");
            xml.writeDefaultNamespace(NAMESPACE);
            xml.writeStartElement("PmtRtr");

            xml.writeStartElement("GrpHdr");
            element(xml, "MsgId", "M" + evento.payer().ispb() + MSG_ID.format(clock.instant()) + evento.endToEndId().substring(21));
            element(xml, "CreDtTm", clock.instant().toString());
            element(xml, "NbOfTxs", "1");
            xml.writeEndElement();

            xml.writeStartElement("TxInf");
            element(xml, "RtrId", evento.endToEndId());
            element(xml, "OrgnlEndToEndId", evento.originalEndToEndId());
            xml.writeStartElement("RtrdIntrBkSttlmAmt");
            xml.writeAttribute("Ccy", evento.currency());
            xml.writeCharacters(evento.amount().toPlainString());
            xml.writeEndElement();
            xml.writeStartElement("RtrRsnInf");
            xml.writeStartElement("Rsn");
            element(xml, "Cd", evento.refundReasonCode());
            xml.writeEndElement();
            xml.writeEndElement();
            // quem devolve é o recebedor original; quem recebe de volta é o pagador original
            agente(xml, "InstgAgt", evento.payer().ispb());
            agente(xml, "InstdAgt", evento.payee().ispb());
            xml.writeEndElement();

            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Falha ao gerar pacs.004 de " + evento.endToEndId(), e);
        }
        return out.toString();
    }

    private static void agente(XMLStreamWriter xml, String papel, String ispb) throws XMLStreamException {
        xml.writeStartElement(papel);
        xml.writeStartElement("FinInstnId");
        xml.writeStartElement("ClrSysMmbId");
        element(xml, "MmbId", ispb);
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private static void element(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(value == null ? "" : value);
        xml.writeEndElement();
    }
}

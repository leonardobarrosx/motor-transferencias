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
 * Traduz o evento para uma mensagem ISO 20022 pacs.008 (ordem de crédito entre
 * instituições), o formato que o SPI do Banco Central usa no Pix. Versão
 * simplificada: só os blocos essenciais. O XMLStreamWriter faz o escape de
 * nomes e descrições, então "Padaria <Sol> & Cia" não quebra o documento.
 */
@Component
public class Pacs008 {

    static final String NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08";
    private static final DateTimeFormatter MSG_ID = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final Clock clock;

    public Pacs008(Clock clock) {
        this.clock = clock;
    }

    public String gerar(TransferEvent evento) {
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter xml = XMLOutputFactory.newInstance().createXMLStreamWriter(out);
            xml.writeStartDocument("UTF-8", "1.0");
            xml.writeStartElement("Document");
            xml.writeDefaultNamespace(NAMESPACE);
            xml.writeStartElement("FIToFICstmrCdtTrf");

            xml.writeStartElement("GrpHdr");
            element(xml, "MsgId", "M" + evento.payer().ispb() + MSG_ID.format(clock.instant()) + evento.endToEndId().substring(21));
            element(xml, "CreDtTm", clock.instant().toString());
            element(xml, "NbOfTxs", "1");
            xml.writeStartElement("SttlmInf");
            element(xml, "SttlmMtd", "CLRG");
            xml.writeEndElement();
            xml.writeEndElement();

            xml.writeStartElement("CdtTrfTxInf");
            xml.writeStartElement("PmtId");
            element(xml, "EndToEndId", evento.endToEndId());
            xml.writeEndElement();
            xml.writeStartElement("IntrBkSttlmAmt");
            xml.writeAttribute("Ccy", evento.currency());
            xml.writeCharacters(evento.amount().toPlainString());
            xml.writeEndElement();
            element(xml, "ChrgBr", "SLEV");
            parte(xml, "Dbtr", evento.payer());
            parte(xml, "Cdtr", evento.payee());
            if (evento.description() != null) {
                xml.writeStartElement("RmtInf");
                element(xml, "Ustrd", evento.description());
                xml.writeEndElement();
            }
            xml.writeEndElement();

            xml.writeEndElement();
            xml.writeEndElement();
            xml.writeEndDocument();
            xml.close();
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Falha ao gerar pacs.008 de " + evento.endToEndId(), e);
        }
        return out.toString();
    }

    /** Dbtr/DbtrAcct/DbtrAgt ou Cdtr/CdtrAcct/CdtrAgt. */
    private static void parte(XMLStreamWriter xml, String papel, TransferEvent.Party parte) throws XMLStreamException {
        xml.writeStartElement(papel);
        element(xml, "Nm", parte.holderName());
        xml.writeStartElement("Id");
        xml.writeStartElement(parte.taxId().length() == 14 ? "OrgId" : "PrvtId");
        xml.writeStartElement("Othr");
        element(xml, "Id", parte.taxId());
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();

        xml.writeStartElement(papel + "Acct");
        xml.writeStartElement("Id");
        xml.writeStartElement("Othr");
        element(xml, "Id", parte.accountNumber());
        element(xml, "Issr", parte.branch());
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeStartElement("Prxy");
        element(xml, "Id", parte.pixKey());
        xml.writeEndElement();
        xml.writeEndElement();

        xml.writeStartElement(papel + "Agt");
        xml.writeStartElement("FinInstnId");
        xml.writeStartElement("ClrSysMmbId");
        element(xml, "MmbId", parte.ispb());
        xml.writeEndElement();
        xml.writeEndElement();
        xml.writeEndElement();
    }

    private static void element(XMLStreamWriter xml, String name, String value) throws XMLStreamException {
        xml.writeStartElement(name);
        xml.writeCharacters(value);
        xml.writeEndElement();
    }
}

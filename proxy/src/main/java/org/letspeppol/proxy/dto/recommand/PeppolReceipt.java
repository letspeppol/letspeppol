package org.letspeppol.proxy.dto.recommand;

import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public record PeppolReceipt(String messageId, Instant timestamp) {

    private static final String EBMS_NAMESPACE = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/";
    private static final PeppolReceipt EMPTY = new PeppolReceipt(null, null);

    public static PeppolReceipt parse(String xml, String sentMessageId) {
        if (xml == null || xml.isBlank()) {
            return EMPTY;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            var document = builder.parse(new InputSource(new StringReader(xml)));
            var signals = document.getElementsByTagNameNS(EBMS_NAMESPACE, "SignalMessage");
            for (int i = 0; i < signals.getLength(); i++) {
                Element signal = (Element) signals.item(i);
                if (child(signal, "Receipt") == null || child(signal, "Error") != null) {
                    continue;
                }
                Element info = child(signal, "MessageInfo");
                if (info == null) {
                    continue;
                }
                String reference = text(info, "RefToMessageId");
                if (sentMessageId != null && !sentMessageId.equals(reference)) {
                    continue;
                }
                // The receipt's MessageId belongs to C3; RefToMessageId belongs to C2.
                return new PeppolReceipt(text(info, "MessageId"), timestamp(text(info, "Timestamp")));
            }
        } catch (Exception ignored) {
            // Missing or unreadable receipt metadata does not mean delivery was rejected.
        }
        return EMPTY;
    }

    private static Element child(Element parent, String name) {
        for (var node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && EBMS_NAMESPACE.equals(element.getNamespaceURI())
                    && name.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    private static String text(Element parent, String name) {
        Element element = child(parent, name);
        if (element == null || element.getTextContent().isBlank()) {
            return null;
        }
        return element.getTextContent().trim();
    }

    private static Instant timestamp(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}

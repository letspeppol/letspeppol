package org.letspeppol.proxy.util;

import org.letspeppol.proxy.model.DocumentType;
import org.letspeppol.proxy.model.UblDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;

/** The initial PHOSS transport supports Peppol BIS Billing invoices, not arbitrary XML. */
public final class PhossEnvelope {
    public static final String SBD = "http://www.unece.org/cefact/namespaces/StandardBusinessDocumentHeader";
    public static final String INVOICE_NS = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    public static final String CUSTOMIZATION = "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0";
    public static final String DOCUMENT_ID = INVOICE_NS + "::Invoice##" + CUSTOMIZATION + "::2.1";
    public static final String PROCESS_ID = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
    private static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    private PhossEnvelope() {}

    public static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler());
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    public static String create(UblDocument source, String instanceId, Instant created, String country) {
        try {
            if (source.getType() != DocumentType.INVOICE || UblParser.parseDocumentType(source.getUbl()) != DocumentType.INVOICE) {
                throw new IllegalArgumentException("PHOSS currently supports BIS Billing Invoice only");
            }
            var ubl = parse(source.getUbl());
            Element invoice = ubl.getDocumentElement();
            if (!INVOICE_NS.equals(invoice.getNamespaceURI()) || !"Invoice".equals(invoice.getLocalName())
                    || !CUSTOMIZATION.equals(text(invoice, "CustomizationID"))
                    || !PROCESS_ID.equals(text(invoice, "ProfileID"))) {
                throw new IllegalArgumentException("Unsupported PHOSS invoice metadata");
            }
            var parties = UblParser.parsePeppolParties(source.getUbl());
            if (!source.getOwnerPeppolId().equals(parties.sender())
                    || !source.getPartnerPeppolId().equals(parties.receiver())) {
                throw new IllegalArgumentException("PHOSS envelope parties must match UBL endpoints");
            }
            requireParticipant(parties.sender());
            requireParticipant(parties.receiver());
            if (country == null || !country.matches("[A-Z]{2}")) {
                throw new IllegalArgumentException("PHOSS requires a two-letter C1 country");
            }
            Document sbd = parse("<StandardBusinessDocument xmlns=\"" + SBD + "\"/>");
            Element root = sbd.getDocumentElement();
            Element header = add(root, "StandardBusinessDocumentHeader", null);
            add(header, "HeaderVersion", "1.0");
            for (String role : new String[]{"Sender", "Receiver"}) {
                Element id = add(add(header, role, null), "Identifier",
                        role.equals("Sender") ? parties.sender() : parties.receiver());
                id.setAttribute("Authority", "iso6523-actorid-upis");
            }
            Element identification = add(header, "DocumentIdentification", null);
            add(identification, "Standard", INVOICE_NS);
            add(identification, "TypeVersion", "2.1");
            add(identification, "InstanceIdentifier", instanceId);
            add(identification, "Type", "Invoice");
            add(identification, "CreationDateAndTime", created.toString());
            Element scopes = add(header, "BusinessScope", null);
            scope(scopes, "DOCUMENTID", DOCUMENT_ID, "busdox-docid-qns");
            scope(scopes, "PROCESSID", PROCESS_ID, "cenbii-procid-ubl");
            scope(scopes, "COUNTRY_C1", country, null);
            root.appendChild(sbd.importNode(invoice, true));
            var factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var writer = new StringWriter();
            factory.newTransformer().transform(new DOMSource(sbd), new StreamResult(writer));
            return writer.toString();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // Do not echo the invoice, endpoints or XML parser diagnostics to callers/logs.
            throw new IllegalArgumentException("Cannot construct PHOSS invoice envelope");
        }
    }

    public static void requireParticipant(String id) {
        if (id == null || !id.matches("[0-9]{4}:[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("Invalid PHOSS participant identifier");
        }
    }

    private static String text(Element parent, String name) {
        for (var n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && CBC.equals(e.getNamespaceURI()) && name.equals(e.getLocalName())) {
                return e.getTextContent().trim();
            }
        }
        return "";
    }

    private static Element add(Element parent, String name, String text) {
        Element e = parent.getOwnerDocument().createElementNS(SBD, name);
        if (text != null) e.setTextContent(text);
        parent.appendChild(e);
        return e;
    }

    private static void scope(Element parent, String type, String value, String scheme) {
        Element scope = add(parent, "Scope", null);
        add(scope, "Type", type);
        add(scope, "InstanceIdentifier", value);
        if (scheme != null) add(scope, "Identifier", scheme);
    }
}

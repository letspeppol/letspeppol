package org.letspeppol.proxy.util;

import org.letspeppol.proxy.dto.PeppolParties;
//import org.letspeppol.proxy.dto.UblDto;
//import org.letspeppol.proxy.model.DocumentType;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;

public final class UblParser {

    private static Document prepareDomDocument(String xml) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true); //Safety if forgotten to add local-name()
        documentBuilderFactory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true); //Sets protective limits and disables risky stuff
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //Forbids DOCTYPE and DTD processing
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false); //Disables resolving external general entities
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); //Disables resolving external parameter entities
        return documentBuilderFactory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static PeppolParties getPeppolParties(Document document) throws XPathExpressionException {
        XPath xp = XPathFactory.newInstance().newXPath();
        String senderId = xp.evaluate("normalize-space((/*/*[local-name()='AccountingSupplierParty']/*[local-name()='Party']/*[local-name()='EndpointID'])[1])", document).trim();
        String senderScheme = xp.evaluate("normalize-space((/*/*[local-name()='AccountingSupplierParty']/*[local-name()='Party']/*[local-name()='EndpointID'])[1]/@schemeID)", document).trim();
        if (senderId.isEmpty()) {
            throw new RuntimeException("AccountingSupplierParty not found");
        }
        String senderPeppolId = senderScheme.isEmpty() ? senderId : senderScheme + ":" + senderId;

        String receiverId = xp.evaluate("normalize-space((/*/*[local-name()='AccountingCustomerParty']/*[local-name()='Party']/*[local-name()='EndpointID'])[1])", document).trim();
        String receiverScheme = xp.evaluate("normalize-space((/*/*[local-name()='AccountingCustomerParty']/*[local-name()='Party']/*[local-name()='EndpointID'])[1]/@schemeID)", document).trim();
        if (receiverId.isEmpty()) {
            throw new RuntimeException("AccountingCustomerParty not found");
        }
        String receiverPeppolId = receiverScheme.isEmpty() ? receiverId : receiverScheme + ":" + receiverId;

        return new PeppolParties(senderPeppolId, receiverPeppolId);
    }

    public static PeppolParties parsePeppolParties(String xml) throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        return getPeppolParties(prepareDomDocument(xml));
    }
}

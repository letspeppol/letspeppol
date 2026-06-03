package org.letspeppol.app.util;

import org.letspeppol.app.dto.PeppolParties;
import org.letspeppol.app.dto.UblDto;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;
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
import java.time.*;
import java.util.Currency;

public final class UblParser {

    private static Document prepareDomDocument(String xml) throws ParserConfigurationException, IOException, SAXException {
        // Strip a leading UTF-8 BOM if present \u2014 SAX rejects it as "Content is not allowed in prolog".
        if (xml != null && !xml.isEmpty() && xml.charAt(0) == '\uFEFF') {
            xml = xml.substring(1);
        }
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

    private static String firstNonEmpty(XPath xp, Document document, String... paths) throws XPathExpressionException {
        for (String path : paths) {
            String value = xp.evaluate(path, document).trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    public static UblDto parse(DocumentDirection documentDirection, String xml) throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        Document document = prepareDomDocument(xml);
        XPath xp = XPathFactory.newInstance().newXPath();
        PeppolParties peppolParties = getPeppolParties(document);

        // partnerName (customer name -> legal name -> supplier name -> supplier legal name)
        String[] xpaths = switch (documentDirection) {
            case OUTGOING -> new String[] {
                "/*/*[local-name()='AccountingCustomerParty']/*[local-name()='Party']/*[local-name()='PartyName']/*[local-name()='Name']",
                "/*/*[local-name()='AccountingCustomerParty']/*[local-name()='Party']/*[local-name()='PartyLegalEntity']/*[local-name()='RegistrationName']"
            };
            case INCOMING -> new String[] {
                "/*/*[local-name()='AccountingSupplierParty']/*[local-name()='Party']/*[local-name()='PartyName']/*[local-name()='Name']",
                "/*/*[local-name()='AccountingSupplierParty']/*[local-name()='Party']/*[local-name()='PartyLegalEntity']/*[local-name()='RegistrationName']"
            };
        };

        String partnerName = null;
        for (String path : xpaths) {
            String value = xp.evaluate(path, document).trim();
            if (!value.isEmpty()) {
                partnerName = value;
                break;
            }
        }

        // invoiceReference
        String invoiceReference = xp.evaluate("/*/*[local-name()='ID']", document).trim();
        if (invoiceReference.isEmpty()) invoiceReference = null;

        // buyerReference
        String buyerReference = xp.evaluate("/*/*[local-name()='BuyerReference']", document).trim();
        if (buyerReference.isEmpty()) buyerReference = null;

        // orderReference
        String orderReference = xp.evaluate("/*/*[local-name()='OrderReference']/*[local-name()='ID']", document).trim();
        if (orderReference.isEmpty()) orderReference = null;

        // type (from root local name)
        DocumentType type = "Invoice".equals(document.getDocumentElement().getLocalName()) ? DocumentType.INVOICE
                : "CreditNote".equals(document.getDocumentElement().getLocalName()) ? DocumentType.CREDIT_NOTE
                : null;

        // Per-path evaluation, not an XPath union — `(A|B|C)[1]` returns document-order first, not listed-order.
        String currencyCode = firstNonEmpty(xp, document,
                "/*/*[local-name()='LegalMonetaryTotal']/*[local-name()='PayableAmount']/@currencyID",
                "/*/*[local-name()='LegalMonetaryTotal']/*[local-name()='TaxInclusiveAmount']/@currencyID",
                "/*/*[local-name()='LegalMonetaryTotal']/*[local-name()='TaxExclusiveAmount']/@currencyID");
        Currency currency = currencyCode.isEmpty() ? null : Currency.getInstance(currencyCode.toUpperCase());

        // Fall back to TaxInclusive then TaxExclusive (zero-VAT case) only when PayableAmount is absent.
        String inclStr = firstNonEmpty(xp, document,
                "/*/*[local-name()='LegalMonetaryTotal']/*[local-name()='PayableAmount']",
                "/*/*[local-name()='LegalMonetaryTotal']/*[local-name()='TaxInclusiveAmount']",
                "/*/*[local-name()='LegalMonetaryTotal']/*[local-name()='TaxExclusiveAmount']");
        BigDecimal amountInclVat = inclStr.isEmpty() ? null : new BigDecimal(inclStr);

        // No fallback to PayableAmount — that would silently report gross as net.
        String exclStr = firstNonEmpty(xp, document,
                "/*/*[local-name()='LegalMonetaryTotal']/*[local-name()='TaxExclusiveAmount']");
        BigDecimal amountExclVat = exclStr.isEmpty() ? null : new BigDecimal(exclStr);

        // issueDate (YYYY-MM-DD -> midnight UTC)
        String issueStr = xp.evaluate("normalize-space(/*/*[local-name()='IssueDate'])", document).trim();
        Instant issueDate = issueStr.isEmpty() ? null : LocalDate.parse(issueStr).atStartOfDay(ZoneOffset.UTC).toInstant();

        // dueDate (DueDate or PaymentDueDate)
        String dueStr = xp.evaluate("normalize-space((/*/*[local-name()='DueDate']"
                + " | /*/*[local-name()='PaymentMeans']/*[local-name()='PaymentDueDate'])[1])", document).trim();
        Instant dueDate = dueStr.isEmpty() ? null : LocalDate.parse(dueStr).atStartOfDay(ZoneOffset.UTC).toInstant();

        // paymentTerms (Note or PaymentTermsDetails)
        String paymentTerms = xp.evaluate("normalize-space((/*/*[local-name()='PaymentTerms']/*[local-name()='Note']"
                + " | /*/*[local-name()='PaymentTerms']/*[local-name()='PaymentTermsDetails'])[1])", document).trim();
        if (paymentTerms.isEmpty()) paymentTerms = null;

        return new UblDto(peppolParties.sender(), peppolParties.receiver(), partnerName, invoiceReference, buyerReference, orderReference, type, currency, amountInclVat, amountExclVat, issueDate, dueDate, paymentTerms);
    }
}

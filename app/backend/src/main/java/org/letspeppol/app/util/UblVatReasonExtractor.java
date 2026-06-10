package org.letspeppol.app.util;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.InputSource;

public final class UblVatReasonExtractor {

    private UblVatReasonExtractor() {}

    public record VatReasonUsage(String selectedTaxCategoryId, String writtenReason) {}

    public static List<VatReasonUsage> extract(String xml) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        try {
            Document document = prepareDomDocument(xml);
            XPath xp = XPathFactory.newInstance().newXPath();
            NodeList lines = (NodeList) xp.evaluate(
                    "/*/*[local-name()='InvoiceLine' or local-name()='CreditNoteLine']",
                    document,
                    XPathConstants.NODESET
            );

            List<VatReasonUsage> reasons = new ArrayList<>();
            for (int i = 0; i < lines.getLength(); i++) {
                Node line = lines.item(i);
                String categoryId = xp.evaluate(
                        "normalize-space(./*[local-name()='Item']/*[local-name()='ClassifiedTaxCategory']/*[local-name()='ID'])",
                        line
                ).trim();
                String reason = xp.evaluate(
                        "normalize-space(./*[local-name()='Item']/*[local-name()='ClassifiedTaxCategory']/*[local-name()='TaxExemptionReason'])",
                        line
                ).trim();
                if (!categoryId.isEmpty() && !reason.isEmpty()) {
                    reasons.add(new VatReasonUsage(categoryId, reason));
                }
            }
            return reasons;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Document prepareDomDocument(String xml) throws Exception {
        if (!xml.isEmpty() && xml.charAt(0) == '\uFEFF') {
            xml = xml.substring(1);
        }
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        documentBuilderFactory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return documentBuilderFactory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
}

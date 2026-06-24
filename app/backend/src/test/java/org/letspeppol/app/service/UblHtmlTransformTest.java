package org.letspeppol.app.service;

import org.junit.jupiter.api.Test;

import javax.xml.XMLConstants;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UblHtmlTransformTest {

    @Test
    void invoiceTransformAddsZeroVatFootnoteMarkerAndText() throws Exception {
        String html = transform(
                "pdf/ubl-invoice-to-html.xsl",
                """
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>INV-FOOTNOTE</cbc:ID>
                    <cbc:IssueDate>2026-01-05</cbc:IssueDate>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
                    <cac:AccountingSupplierParty><cac:Party><cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName></cac:Party></cac:AccountingSupplierParty>
                    <cac:AccountingCustomerParty><cac:Party><cac:PartyName><cbc:Name>Customer BV</cbc:Name></cac:PartyName></cac:Party></cac:AccountingCustomerParty>
                    <cac:InvoiceLine>
                        <cbc:ID>1</cbc:ID>
                        <cbc:InvoicedQuantity unitCode="H87">1</cbc:InvoicedQuantity>
                        <cbc:LineExtensionAmount currencyID="EUR">100.00</cbc:LineExtensionAmount>
                        <cac:Item>
                            <cbc:Name>Consulting services</cbc:Name>
                            <cac:ClassifiedTaxCategory>
                                <cbc:ID>AE</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>Reverse charge due to article 44</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">100.00</cbc:PriceAmount></cac:Price>
                    </cac:InvoiceLine>
                    <cac:TaxTotal><cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount></cac:TaxTotal>
                    <cac:LegalMonetaryTotal>
                        <cbc:TaxExclusiveAmount currencyID="EUR">100.00</cbc:TaxExclusiveAmount>
                        <cbc:PayableAmount currencyID="EUR">100.00</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </Invoice>
                """
        );

        assertTrue(html.contains("0<span>%</span><sup class=\"footnote-ref\">1</sup>"), html);
        assertTrue(html.contains("0% VAT notes"), html);
        assertTrue(html.contains("<strong>Reverse Charge</strong> : Reverse charge due to article 44"), html);
        assertFalse(html.contains("Reason type:"), html);
        assertFalse(html.contains("Explanation:"), html);
    }

    @Test
    void creditNoteTransformAddsZeroVatFootnoteMarkerAndText() throws Exception {
        String html = transform(
                "pdf/ubl-creditnote-to-html.xsl",
                """
                <CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"
                            xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                            xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>CN-FOOTNOTE</cbc:ID>
                    <cbc:IssueDate>2026-01-05</cbc:IssueDate>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
                    <cac:AccountingSupplierParty><cac:Party><cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName></cac:Party></cac:AccountingSupplierParty>
                    <cac:AccountingCustomerParty><cac:Party><cac:PartyName><cbc:Name>Customer BV</cbc:Name></cac:PartyName></cac:Party></cac:AccountingCustomerParty>
                    <cac:CreditNoteLine>
                        <cbc:ID>1</cbc:ID>
                        <cbc:CreditedQuantity unitCode="H87">1</cbc:CreditedQuantity>
                        <cbc:LineExtensionAmount currencyID="EUR">100.00</cbc:LineExtensionAmount>
                        <cac:Item>
                            <cbc:Name>Credit for consulting services</cbc:Name>
                            <cac:ClassifiedTaxCategory>
                                <cbc:ID>AE</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>Reverse charge due to article 44</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">100.00</cbc:PriceAmount></cac:Price>
                    </cac:CreditNoteLine>
                    <cac:TaxTotal><cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount></cac:TaxTotal>
                    <cac:LegalMonetaryTotal>
                        <cbc:TaxExclusiveAmount currencyID="EUR">100.00</cbc:TaxExclusiveAmount>
                        <cbc:PayableAmount currencyID="EUR">100.00</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </CreditNote>
                """
        );

        assertTrue(html.contains("0<span>%</span><sup class=\"footnote-ref\">1</sup>"), html);
        assertTrue(html.contains("0% VAT notes"), html);
        assertTrue(html.contains("<strong>Reverse Charge</strong> : Reverse charge due to article 44"), html);
        assertFalse(html.contains("Reason type:"), html);
        assertFalse(html.contains("Explanation:"), html);
    }

    @Test
    void invoiceTransformReusesFootnoteNumberForDuplicateZeroVatNotes() throws Exception {
        String html = transform(
                "pdf/ubl-invoice-to-html.xsl",
                """
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>INV-DUPLICATE-FOOTNOTE</cbc:ID>
                    <cbc:IssueDate>2026-01-05</cbc:IssueDate>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
                    <cac:AccountingSupplierParty><cac:Party><cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName></cac:Party></cac:AccountingSupplierParty>
                    <cac:AccountingCustomerParty><cac:Party><cac:PartyName><cbc:Name>Customer BV</cbc:Name></cac:PartyName></cac:Party></cac:AccountingCustomerParty>

                    <cac:InvoiceLine>
                        <cbc:ID>1</cbc:ID>
                        <cbc:InvoicedQuantity unitCode="H87">1</cbc:InvoicedQuantity>
                        <cbc:LineExtensionAmount currencyID="EUR">100.00</cbc:LineExtensionAmount>
                        <cac:Item>
                            <cbc:Name>Consulting services</cbc:Name>
                            <cac:ClassifiedTaxCategory>
                                <cbc:ID>AE</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>due to article 44</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">100.00</cbc:PriceAmount></cac:Price>
                    </cac:InvoiceLine>

                    <cac:InvoiceLine>
                        <cbc:ID>2</cbc:ID>
                        <cbc:InvoicedQuantity unitCode="H87">1</cbc:InvoicedQuantity>
                        <cbc:LineExtensionAmount currencyID="EUR">50.00</cbc:LineExtensionAmount>
                        <cac:Item>
                            <cbc:Name>Follow-up services</cbc:Name>
                            <cac:ClassifiedTaxCategory>
                                <cbc:ID>AE</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>due to article 44</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">50.00</cbc:PriceAmount></cac:Price>
                    </cac:InvoiceLine>

                    <cac:InvoiceLine>
                        <cbc:ID>3</cbc:ID>
                        <cbc:InvoicedQuantity unitCode="H87">1</cbc:InvoicedQuantity>
                        <cbc:LineExtensionAmount currencyID="EUR">25.00</cbc:LineExtensionAmount>
                        <cac:Item>
                            <cbc:Name>Export services</cbc:Name>
                            <cac:ClassifiedTaxCategory>
                                <cbc:ID>G</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>export outside EU</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">25.00</cbc:PriceAmount></cac:Price>
                    </cac:InvoiceLine>

                    <cac:TaxTotal><cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount></cac:TaxTotal>
                    <cac:LegalMonetaryTotal>
                        <cbc:TaxExclusiveAmount currencyID="EUR">175.00</cbc:TaxExclusiveAmount>
                        <cbc:PayableAmount currencyID="EUR">175.00</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </Invoice>
                """
        );

        assertEquals(2, countOccurrences(html, "0<span>%</span><sup class=\"footnote-ref\">1</sup>"), html);
        assertEquals(1, countOccurrences(html, "0<span>%</span><sup class=\"footnote-ref\">2</sup>"), html);
        assertEquals(1, countOccurrences(html, "<strong>Reverse Charge</strong> : due to article 44"), html);
        assertEquals(1, countOccurrences(html, "<strong>Free export item, VAT not charged</strong> : export outside EU"), html);
    }

    @Test
    void creditNoteTransformReusesFootnoteNumberForDuplicateZeroVatNotes() throws Exception {
        String html = transform(
                "pdf/ubl-creditnote-to-html.xsl",
                """
                <CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"
                            xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                            xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>CN-DUPLICATE-FOOTNOTE</cbc:ID>
                    <cbc:IssueDate>2026-01-05</cbc:IssueDate>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
                    <cac:AccountingSupplierParty><cac:Party><cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName></cac:Party></cac:AccountingSupplierParty>
                    <cac:AccountingCustomerParty><cac:Party><cac:PartyName><cbc:Name>Customer BV</cbc:Name></cac:PartyName></cac:Party></cac:AccountingCustomerParty>

                    <cac:CreditNoteLine>
                        <cbc:ID>1</cbc:ID>
                        <cbc:CreditedQuantity unitCode="H87">1</cbc:CreditedQuantity>
                        <cbc:LineExtensionAmount currencyID="EUR">100.00</cbc:LineExtensionAmount>
                        <cac:Item>
                            <cbc:Name>Credit for consulting services</cbc:Name>
                            <cac:ClassifiedTaxCategory>
                                <cbc:ID>AE</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>due to article 44</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">100.00</cbc:PriceAmount></cac:Price>
                    </cac:CreditNoteLine>

                    <cac:CreditNoteLine>
                        <cbc:ID>2</cbc:ID>
                        <cbc:CreditedQuantity unitCode="H87">1</cbc:CreditedQuantity>
                        <cbc:LineExtensionAmount currencyID="EUR">50.00</cbc:LineExtensionAmount>
                        <cac:Item>
                            <cbc:Name>Credit for follow-up services</cbc:Name>
                            <cac:ClassifiedTaxCategory>
                                <cbc:ID>AE</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>due to article 44</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">50.00</cbc:PriceAmount></cac:Price>
                    </cac:CreditNoteLine>

                    <cac:CreditNoteLine>
                        <cbc:ID>3</cbc:ID>
                        <cbc:CreditedQuantity unitCode="H87">1</cbc:CreditedQuantity>
                        <cbc:LineExtensionAmount currencyID="EUR">25.00</cbc:LineExtensionAmount>
                        <cac:Item>
                            <cbc:Name>Credit for export services</cbc:Name>
                            <cac:ClassifiedTaxCategory>
                                <cbc:ID>G</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>export outside EU</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">25.00</cbc:PriceAmount></cac:Price>
                    </cac:CreditNoteLine>

                    <cac:TaxTotal><cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount></cac:TaxTotal>
                    <cac:LegalMonetaryTotal>
                        <cbc:TaxExclusiveAmount currencyID="EUR">175.00</cbc:TaxExclusiveAmount>
                        <cbc:PayableAmount currencyID="EUR">175.00</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </CreditNote>
                """
        );

        assertEquals(2, countOccurrences(html, "0<span>%</span><sup class=\"footnote-ref\">1</sup>"), html);
        assertEquals(1, countOccurrences(html, "0<span>%</span><sup class=\"footnote-ref\">2</sup>"), html);
        assertEquals(1, countOccurrences(html, "<strong>Reverse Charge</strong> : due to article 44"), html);
        assertEquals(1, countOccurrences(html, "<strong>Free export item, VAT not charged</strong> : export outside EU"), html);
    }

    private String transform(String xsltPath, String ublXml) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

        Templates templates;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(xsltPath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing classpath resource " + xsltPath);
            }
            templates = transformerFactory.newTemplates(new StreamSource(inputStream));
        }

        Transformer transformer = templates.newTransformer();
        StringWriter output = new StringWriter();
        transformer.transform(new StreamSource(new StringReader(ublXml)), new StreamResult(output));
        return output.toString();
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int start = 0;
        while ((start = text.indexOf(needle, start)) >= 0) {
            count++;
            start += needle.length();
        }
        return count;
    }
}

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
                    <cbc:Note>Translated customer note</cbc:Note>
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
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">100.00</cbc:PriceAmount></cac:Price>
                    </cac:InvoiceLine>
                    <cac:TaxTotal>
                        <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                        <cac:TaxSubtotal>
                            <cbc:TaxableAmount currencyID="EUR">100.00</cbc:TaxableAmount>
                            <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                            <cac:TaxCategory>
                                <cbc:ID>AE</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>Verleggingsregeling volgens artikel 44</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:TaxCategory>
                        </cac:TaxSubtotal>
                    </cac:TaxTotal>
                    <cac:LegalMonetaryTotal>
                        <cbc:TaxExclusiveAmount currencyID="EUR">100.00</cbc:TaxExclusiveAmount>
                        <cbc:PayableAmount currencyID="EUR">100.00</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </Invoice>
                """
        );

        assertTrue(html.contains("<th style=\"width: 10%\" class=\"amount\">Tax</th>"), html);
        assertTrue(html.contains("0<span>%</span><sup class=\"footnote-ref\">1</sup>"), html);
        assertTrue(html.contains("0% VAT notes"), html);
        assertTrue(html.contains("<strong>Reverse Charge</strong> : Verleggingsregeling volgens artikel 44"), html);
        assertInOrder(html, "Translated customer note", "0% VAT notes");
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
                    <cbc:Note>Translated credit note</cbc:Note>
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
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">100.00</cbc:PriceAmount></cac:Price>
                    </cac:CreditNoteLine>
                    <cac:TaxTotal>
                        <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                        <cac:TaxSubtotal>
                            <cbc:TaxableAmount currencyID="EUR">100.00</cbc:TaxableAmount>
                            <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                            <cac:TaxCategory>
                                <cbc:ID>AE</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>Autoliquidation selon article 44</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:TaxCategory>
                        </cac:TaxSubtotal>
                    </cac:TaxTotal>
                    <cac:LegalMonetaryTotal>
                        <cbc:TaxExclusiveAmount currencyID="EUR">100.00</cbc:TaxExclusiveAmount>
                        <cbc:PayableAmount currencyID="EUR">100.00</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </CreditNote>
                """
        );

        assertTrue(html.contains("<th style=\"width: 10%\" class=\"amount\">Tax</th>"), html);
        assertTrue(html.contains("0<span>%</span><sup class=\"footnote-ref\">1</sup>"), html);
        assertTrue(html.contains("0% VAT notes"), html);
        assertTrue(html.contains("<strong>Reverse Charge</strong> : Autoliquidation selon article 44"), html);
        assertInOrder(html, "Translated credit note", "0% VAT notes");
        assertFalse(html.contains("Reason type:"), html);
        assertFalse(html.contains("Explanation:"), html);
    }

    @Test
    void invoiceTransformFallsBackToEnterpriseNumberWhenVatIsMissing() throws Exception {
        String html = transform(
                "pdf/ubl-invoice-to-html.xsl",
                """
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>INV-ENTERPRISE-FALLBACK</cbc:ID>
                    <cbc:IssueDate>2026-01-05</cbc:IssueDate>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
                    <cac:AccountingSupplierParty>
                        <cac:Party>
                            <cbc:EndpointID schemeID="0208">SUP-ENDPOINT-0208</cbc:EndpointID>
                            <cac:PartyIdentification><cbc:ID>SUP-ID-001</cbc:ID></cac:PartyIdentification>
                            <cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName>
                            <cac:PartyTaxScheme>
                                <cbc:CompanyID>SUP-VAT-001</cbc:CompanyID>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:PartyTaxScheme>
                            <cac:PartyLegalEntity><cbc:CompanyID>SUP-ENT-001</cbc:CompanyID></cac:PartyLegalEntity>
                        </cac:Party>
                    </cac:AccountingSupplierParty>
                    <cac:AccountingCustomerParty>
                        <cac:Party>
                            <cbc:EndpointID schemeID="0208">CUST-ENDPOINT-0208</cbc:EndpointID>
                            <cac:PartyIdentification><cbc:ID>CUST-ID-002</cbc:ID></cac:PartyIdentification>
                            <cac:PartyName><cbc:Name>Customer BV</cbc:Name></cac:PartyName>
                            <cac:PartyLegalEntity><cbc:CompanyID>CUST-ENT-002</cbc:CompanyID></cac:PartyLegalEntity>
                        </cac:Party>
                    </cac:AccountingCustomerParty>
                    <cac:LegalMonetaryTotal>
                        <cbc:PayableAmount currencyID="EUR">12.34</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </Invoice>
                """
        );

        assertTrue(html.contains("<div class=\"muted\">SUP-VAT-001</div>"), html);
        assertTrue(html.contains("<div class=\"muted\">CUST-ENT-002</div>"), html);
        assertFalse(html.contains("SUP-ENT-001"), html);
        assertFalse(html.contains("SUP-ID-001"), html);
        assertFalse(html.contains("CUST-ID-002"), html);
        assertFalse(html.contains("SUP-ENDPOINT-0208"), html);
        assertFalse(html.contains("CUST-ENDPOINT-0208"), html);
    }

    @Test
    void creditNoteTransformFallsBackToEnterpriseNumberWhenVatIsMissing() throws Exception {
        String html = transform(
                "pdf/ubl-creditnote-to-html.xsl",
                """
                <CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"
                            xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                            xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>CN-ENTERPRISE-FALLBACK</cbc:ID>
                    <cbc:IssueDate>2026-01-05</cbc:IssueDate>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
                    <cac:AccountingSupplierParty>
                        <cac:Party>
                            <cbc:EndpointID schemeID="0208">SUP-ENDPOINT-0208</cbc:EndpointID>
                            <cac:PartyIdentification><cbc:ID>SUP-ID-001</cbc:ID></cac:PartyIdentification>
                            <cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName>
                            <cac:PartyTaxScheme>
                                <cbc:CompanyID>SUP-VAT-001</cbc:CompanyID>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:PartyTaxScheme>
                            <cac:PartyLegalEntity><cbc:CompanyID>SUP-ENT-001</cbc:CompanyID></cac:PartyLegalEntity>
                        </cac:Party>
                    </cac:AccountingSupplierParty>
                    <cac:AccountingCustomerParty>
                        <cac:Party>
                            <cbc:EndpointID schemeID="0208">CUST-ENDPOINT-0208</cbc:EndpointID>
                            <cac:PartyIdentification><cbc:ID>CUST-ID-002</cbc:ID></cac:PartyIdentification>
                            <cac:PartyName><cbc:Name>Customer BV</cbc:Name></cac:PartyName>
                            <cac:PartyLegalEntity><cbc:CompanyID>CUST-ENT-002</cbc:CompanyID></cac:PartyLegalEntity>
                        </cac:Party>
                    </cac:AccountingCustomerParty>
                    <cac:LegalMonetaryTotal>
                        <cbc:PayableAmount currencyID="EUR">-12.34</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </CreditNote>
                """
        );

        assertTrue(html.contains("<div class=\"muted\">SUP-VAT-001</div>"), html);
        assertTrue(html.contains("<div class=\"muted\">CUST-ENT-002</div>"), html);
        assertFalse(html.contains("SUP-ENT-001"), html);
        assertFalse(html.contains("SUP-ID-001"), html);
        assertFalse(html.contains("CUST-ID-002"), html);
        assertFalse(html.contains("SUP-ENDPOINT-0208"), html);
        assertFalse(html.contains("CUST-ENDPOINT-0208"), html);
    }

    @Test
    void invoiceTransformFallsBackToIdentificationWhenVatAndEnterpriseNumberAreMissing() throws Exception {
        String html = transform(
                "pdf/ubl-invoice-to-html.xsl",
                """
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>INV-ID-FALLBACK</cbc:ID>
                    <cbc:IssueDate>2026-01-05</cbc:IssueDate>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
                    <cac:AccountingSupplierParty>
                        <cac:Party>
                            <cbc:EndpointID schemeID="0208">SUP-ENDPOINT-0208</cbc:EndpointID>
                            <cac:PartyIdentification><cbc:ID>SUP-ID-003</cbc:ID></cac:PartyIdentification>
                            <cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName>
                        </cac:Party>
                    </cac:AccountingSupplierParty>
                    <cac:AccountingCustomerParty>
                        <cac:Party>
                            <cbc:EndpointID schemeID="0208">CUST-ENDPOINT-0208</cbc:EndpointID>
                            <cac:PartyIdentification><cbc:ID>CUST-ID-004</cbc:ID></cac:PartyIdentification>
                            <cac:PartyName><cbc:Name>Customer BV</cbc:Name></cac:PartyName>
                        </cac:Party>
                    </cac:AccountingCustomerParty>
                    <cac:LegalMonetaryTotal>
                        <cbc:PayableAmount currencyID="EUR">12.34</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </Invoice>
                """
        );

        assertTrue(html.contains("<div class=\"muted\">SUP-ID-003</div>"), html);
        assertTrue(html.contains("<div class=\"muted\">CUST-ID-004</div>"), html);
        assertFalse(html.contains("SUP-ENDPOINT-0208"), html);
        assertFalse(html.contains("CUST-ENDPOINT-0208"), html);
    }

    @Test
    void creditNoteTransformFallsBackToIdentificationWhenVatAndEnterpriseNumberAreMissing() throws Exception {
        String html = transform(
                "pdf/ubl-creditnote-to-html.xsl",
                """
                <CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"
                            xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                            xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>CN-ID-FALLBACK</cbc:ID>
                    <cbc:IssueDate>2026-01-05</cbc:IssueDate>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
                    <cac:AccountingSupplierParty>
                        <cac:Party>
                            <cbc:EndpointID schemeID="0208">SUP-ENDPOINT-0208</cbc:EndpointID>
                            <cac:PartyIdentification><cbc:ID>SUP-ID-003</cbc:ID></cac:PartyIdentification>
                            <cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName>
                        </cac:Party>
                    </cac:AccountingSupplierParty>
                    <cac:AccountingCustomerParty>
                        <cac:Party>
                            <cbc:EndpointID schemeID="0208">CUST-ENDPOINT-0208</cbc:EndpointID>
                            <cac:PartyIdentification><cbc:ID>CUST-ID-004</cbc:ID></cac:PartyIdentification>
                            <cac:PartyName><cbc:Name>Customer BV</cbc:Name></cac:PartyName>
                        </cac:Party>
                    </cac:AccountingCustomerParty>
                    <cac:LegalMonetaryTotal>
                        <cbc:PayableAmount currencyID="EUR">-12.34</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </CreditNote>
                """
        );

        assertTrue(html.contains("<div class=\"muted\">SUP-ID-003</div>"), html);
        assertTrue(html.contains("<div class=\"muted\">CUST-ID-004</div>"), html);
        assertFalse(html.contains("SUP-ENDPOINT-0208"), html);
        assertFalse(html.contains("CUST-ENDPOINT-0208"), html);
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
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">25.00</cbc:PriceAmount></cac:Price>
                    </cac:InvoiceLine>

                    <cac:TaxTotal>
                        <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                        <cac:TaxSubtotal>
                            <cbc:TaxableAmount currencyID="EUR">150.00</cbc:TaxableAmount>
                            <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                            <cac:TaxCategory>
                                <cbc:ID>AE</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>due to article 44</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:TaxCategory>
                        </cac:TaxSubtotal>
                        <cac:TaxSubtotal>
                            <cbc:TaxableAmount currencyID="EUR">25.00</cbc:TaxableAmount>
                            <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                            <cac:TaxCategory>
                                <cbc:ID>G</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>export outside EU</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:TaxCategory>
                        </cac:TaxSubtotal>
                    </cac:TaxTotal>
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
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">25.00</cbc:PriceAmount></cac:Price>
                    </cac:CreditNoteLine>

                    <cac:TaxTotal>
                        <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                        <cac:TaxSubtotal>
                            <cbc:TaxableAmount currencyID="EUR">150.00</cbc:TaxableAmount>
                            <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                            <cac:TaxCategory>
                                <cbc:ID>AE</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>due to article 44</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:TaxCategory>
                        </cac:TaxSubtotal>
                        <cac:TaxSubtotal>
                            <cbc:TaxableAmount currencyID="EUR">25.00</cbc:TaxableAmount>
                            <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                            <cac:TaxCategory>
                                <cbc:ID>G</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReason>export outside EU</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:TaxCategory>
                        </cac:TaxSubtotal>
                    </cac:TaxTotal>
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

    @Test
    void invoiceTransformOmitsVatColumnAndAddsVatNoteForNotSubjectToVatDocuments() throws Exception {
        String html = transform(
                "pdf/ubl-invoice-to-html.xsl",
                """
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>INV-O</cbc:ID>
                    <cbc:IssueDate>2026-06-24</cbc:IssueDate>
                    <cbc:Note>Not subject invoice note</cbc:Note>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
                    <cac:AccountingSupplierParty><cac:Party><cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName></cac:Party></cac:AccountingSupplierParty>
                    <cac:AccountingCustomerParty><cac:Party><cac:PartyName><cbc:Name>Customer BV</cbc:Name></cac:PartyName></cac:Party></cac:AccountingCustomerParty>
                    <cac:InvoiceLine>
                        <cbc:ID>1</cbc:ID>
                        <cbc:InvoicedQuantity unitCode="C62">1</cbc:InvoicedQuantity>
                        <cbc:LineExtensionAmount currencyID="EUR">100.00</cbc:LineExtensionAmount>
                        <cac:Item>
                            <cbc:Name>Services</cbc:Name>
                            <cac:ClassifiedTaxCategory>
                                <cbc:ID>O</cbc:ID>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">100.00</cbc:PriceAmount></cac:Price>
                    </cac:InvoiceLine>
                    <cac:TaxTotal>
                        <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                        <cac:TaxSubtotal>
                            <cbc:TaxableAmount currencyID="EUR">100.00</cbc:TaxableAmount>
                            <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                            <cac:TaxCategory>
                                <cbc:ID>O</cbc:ID>
                                <cbc:TaxExemptionReason>Not subject to VAT</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:TaxCategory>
                        </cac:TaxSubtotal>
                    </cac:TaxTotal>
                    <cac:LegalMonetaryTotal>
                        <cbc:TaxExclusiveAmount currencyID="EUR">100.00</cbc:TaxExclusiveAmount>
                        <cbc:PayableAmount currencyID="EUR">100.00</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </Invoice>
                """
        );

        assertFalse(html.contains("<th style=\"width: 10%\" class=\"amount\">Tax</th>"), html);
        assertFalse(html.contains("0% VAT notes"), html);
        assertTrue(html.contains("VAT note"), html);
        assertTrue(html.contains("This document is not subject to VAT. Reason: Not subject to VAT."), html);
        assertInOrder(html, "Not subject invoice note", "VAT note");
    }

    @Test
    void creditNoteTransformOmitsVatColumnAndAddsVatNoteForNotSubjectToVatDocuments() throws Exception {
        String html = transform(
                "pdf/ubl-creditnote-to-html.xsl",
                """
                <CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"
                            xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                            xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>CN-O</cbc:ID>
                    <cbc:IssueDate>2026-06-24</cbc:IssueDate>
                    <cbc:Note>Not subject credit note</cbc:Note>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
                    <cac:AccountingSupplierParty><cac:Party><cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName></cac:Party></cac:AccountingSupplierParty>
                    <cac:AccountingCustomerParty><cac:Party><cac:PartyName><cbc:Name>Customer BV</cbc:Name></cac:PartyName></cac:Party></cac:AccountingCustomerParty>
                    <cac:CreditNoteLine>
                        <cbc:ID>1</cbc:ID>
                        <cbc:CreditedQuantity unitCode="C62">1</cbc:CreditedQuantity>
                        <cbc:LineExtensionAmount currencyID="EUR">100.00</cbc:LineExtensionAmount>
                        <cac:Item>
                            <cbc:Name>Services</cbc:Name>
                            <cac:ClassifiedTaxCategory>
                                <cbc:ID>O</cbc:ID>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price><cbc:PriceAmount currencyID="EUR">100.00</cbc:PriceAmount></cac:Price>
                    </cac:CreditNoteLine>
                    <cac:TaxTotal>
                        <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                        <cac:TaxSubtotal>
                            <cbc:TaxableAmount currencyID="EUR">100.00</cbc:TaxableAmount>
                            <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                            <cac:TaxCategory>
                                <cbc:ID>O</cbc:ID>
                                <cbc:TaxExemptionReason>Not subject to VAT</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:TaxCategory>
                        </cac:TaxSubtotal>
                    </cac:TaxTotal>
                    <cac:LegalMonetaryTotal>
                        <cbc:TaxExclusiveAmount currencyID="EUR">100.00</cbc:TaxExclusiveAmount>
                        <cbc:PayableAmount currencyID="EUR">100.00</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </CreditNote>
                """
        );

        assertFalse(html.contains("<th style=\"width: 10%\" class=\"amount\">Tax</th>"), html);
        assertFalse(html.contains("0% VAT notes"), html);
        assertTrue(html.contains("VAT note"), html);
        assertTrue(html.contains("This document is not subject to VAT. Reason: Not subject to VAT."), html);
        assertInOrder(html, "Not subject credit note", "VAT note");
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

    private void assertInOrder(String text, String first, String second) {
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second);

        assertTrue(firstIndex >= 0, text);
        assertTrue(secondIndex >= 0, text);
        assertTrue(firstIndex < secondIndex, text);
    }
}

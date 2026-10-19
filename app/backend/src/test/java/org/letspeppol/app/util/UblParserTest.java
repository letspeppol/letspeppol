package org.letspeppol.app.util;

import org.junit.jupiter.api.Test;
import org.letspeppol.app.dto.UblDto;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class UblParserTest {

    @Test
    void selfBillingReversesTransportPartiesAndSelectsCustomerAsIncomingPartner() throws Exception {
        for (String code : new String[]{"389", "527"}) {
            String xml = invoiceWithMonetaryTotal("<cbc:PayableAmount currencyID=\"EUR\">121.00</cbc:PayableAmount>")
                    .replace("<cbc:ID>INV-1", "<cbc:InvoiceTypeCode>" + code + "</cbc:InvoiceTypeCode><cbc:ID>INV-1");
            UblDto incoming = UblParser.parse(DocumentDirection.INCOMING, xml);
            assertThat(incoming.senderPeppolId()).isEqualTo("0208:9876543210");
            assertThat(incoming.receiverPeppolId()).isEqualTo("0208:0123456789");
            assertThat(incoming.partnerName()).isEqualTo("Customer Ltd");
            assertThat(incoming.type()).isEqualTo(DocumentType.SELF_BILLING_INVOICE);
            assertThat(incoming.amountInclVat()).isEqualByComparingTo("121.00");
            assertThat(UblParser.parse(DocumentDirection.OUTGOING, xml).partnerName()).isEqualTo("Supplier Ltd");
            assertThat(UblParser.parsePeppolParties(xml).receiver()).isEqualTo("0208:0123456789");
        }
    }

    @Test
    void selfBillingProfileAndCreditNoteAreRecognized() throws Exception {
        String invoice = invoiceWithMonetaryTotal("").replace("<cbc:ID>INV-1",
                "<cbc:CustomizationID>urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:selfbilling:3.0</cbc:CustomizationID><cbc:ID>INV-1");
        assertThat(UblParser.parse(DocumentDirection.INCOMING, invoice).partnerName()).isEqualTo("Customer Ltd");
        String creditNote = invoiceWithMonetaryTotal("")
                .replace("Invoice", "CreditNote")
                .replace("<cbc:ID>INV-1", "<cbc:CreditNoteTypeCode>261</cbc:CreditNoteTypeCode><cbc:ID>INV-1");
        UblDto parsed = UblParser.parse(DocumentDirection.INCOMING, creditNote);
        assertThat(parsed.type()).isEqualTo(DocumentType.SELF_BILLING_CREDIT_NOTE);
        assertThat(parsed.receiverPeppolId()).isEqualTo("0208:0123456789");
        assertThat(parsed.partnerName()).isEqualTo("Customer Ltd");
    }

    private static final String SUPPLIER_PARTY = """
            <cac:AccountingSupplierParty>
                <cac:Party>
                    <cbc:EndpointID schemeID="0208">0123456789</cbc:EndpointID>
                    <cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName>
                </cac:Party>
            </cac:AccountingSupplierParty>""";

    private static final String CUSTOMER_PARTY = """
            <cac:AccountingCustomerParty>
                <cac:Party>
                    <cbc:EndpointID schemeID="0208">9876543210</cbc:EndpointID>
                    <cac:PartyName><cbc:Name>Customer Ltd</cbc:Name></cac:PartyName>
                </cac:Party>
            </cac:AccountingCustomerParty>""";

    private static String invoiceWithMonetaryTotal(String monetaryTotalInner) {
        return """
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>INV-1</cbc:ID>
                    <cbc:IssueDate>2026-01-15</cbc:IssueDate>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
                """
                + SUPPLIER_PARTY + CUSTOMER_PARTY
                + "<cac:LegalMonetaryTotal>" + monetaryTotalInner + "</cac:LegalMonetaryTotal>"
                + "</Invoice>";
    }

    @Test
    void picksPayableAmountWhenAllThreePresent() throws Exception {
        // UBL canonical order is TaxExclusive → TaxInclusive → PayableAmount — listed-order matters.
        String xml = invoiceWithMonetaryTotal("""
                <cbc:TaxExclusiveAmount currencyID="EUR">82.64</cbc:TaxExclusiveAmount>
                <cbc:TaxInclusiveAmount currencyID="EUR">100.00</cbc:TaxInclusiveAmount>
                <cbc:PayableAmount currencyID="EUR">100.00</cbc:PayableAmount>
                """);

        UblDto dto = UblParser.parse(DocumentDirection.INCOMING, xml);

        assertThat(dto.amountInclVat()).isEqualByComparingTo("100.00");
        assertThat(dto.amountExclVat()).isEqualByComparingTo("82.64");
        assertThat(dto.currency()).isEqualTo(Currency.getInstance("EUR"));
        assertThat(dto.type()).isEqualTo(DocumentType.INVOICE);
    }

    @Test
    void payableAmountWinsEvenWhenItDiffersFromTaxInclusive() throws Exception {
        // PayableAmount = TaxInclusive − PrepaidAmount; the dashboard wants what's actually owed.
        String xml = invoiceWithMonetaryTotal("""
                <cbc:TaxExclusiveAmount currencyID="EUR">82.64</cbc:TaxExclusiveAmount>
                <cbc:TaxInclusiveAmount currencyID="EUR">100.00</cbc:TaxInclusiveAmount>
                <cbc:PrepaidAmount currencyID="EUR">25.00</cbc:PrepaidAmount>
                <cbc:PayableAmount currencyID="EUR">75.00</cbc:PayableAmount>
                """);

        UblDto dto = UblParser.parse(DocumentDirection.INCOMING, xml);

        assertThat(dto.amountInclVat()).isEqualByComparingTo("75.00");
        assertThat(dto.amountExclVat()).isEqualByComparingTo("82.64");
    }

    @Test
    void fallsBackToTaxInclusiveWhenPayableAbsent() throws Exception {
        String xml = invoiceWithMonetaryTotal("""
                <cbc:TaxExclusiveAmount currencyID="EUR">82.64</cbc:TaxExclusiveAmount>
                <cbc:TaxInclusiveAmount currencyID="EUR">100.00</cbc:TaxInclusiveAmount>
                """);

        UblDto dto = UblParser.parse(DocumentDirection.INCOMING, xml);

        assertThat(dto.amountInclVat()).isEqualByComparingTo("100.00");
        assertThat(dto.amountExclVat()).isEqualByComparingTo("82.64");
        assertThat(dto.currency()).isEqualTo(Currency.getInstance("EUR"));
    }

    @Test
    void fallsBackToTaxExclusiveWhenOnlyThatPresent() throws Exception {
        // Zero-VAT case — both views collapse to the same number.
        String xml = invoiceWithMonetaryTotal("""
                <cbc:TaxExclusiveAmount currencyID="USD">42.00</cbc:TaxExclusiveAmount>
                """);

        UblDto dto = UblParser.parse(DocumentDirection.INCOMING, xml);

        assertThat(dto.amountInclVat()).isEqualByComparingTo("42.00");
        assertThat(dto.amountExclVat()).isEqualByComparingTo("42.00");
        assertThat(dto.currency()).isEqualTo(Currency.getInstance("USD"));
    }

    @Test
    void exclVatIsNullWhenOnlyGrossAmountsArePresent() throws Exception {
        String xml = invoiceWithMonetaryTotal("""
                <cbc:TaxInclusiveAmount currencyID="EUR">100.00</cbc:TaxInclusiveAmount>
                <cbc:PayableAmount currencyID="EUR">100.00</cbc:PayableAmount>
                """);

        UblDto dto = UblParser.parse(DocumentDirection.INCOMING, xml);

        assertThat(dto.amountInclVat()).isEqualByComparingTo("100.00");
        assertThat(dto.amountExclVat()).isNull();
    }

    @Test
    void returnsNullAmountAndCurrencyWhenLegalMonetaryTotalEmpty() throws Exception {
        String xml = invoiceWithMonetaryTotal("");

        UblDto dto = UblParser.parse(DocumentDirection.INCOMING, xml);

        assertThat(dto.amountInclVat()).isNull();
        assertThat(dto.amountExclVat()).isNull();
        assertThat(dto.currency()).isNull();
    }

    @Test
    void parsesXmlPrefixedWithUtf8Bom() throws Exception {
        String xml = "﻿" + invoiceWithMonetaryTotal("""
                <cbc:TaxExclusiveAmount currencyID="EUR">82.64</cbc:TaxExclusiveAmount>
                <cbc:PayableAmount currencyID="EUR">100.00</cbc:PayableAmount>
                """);

        UblDto dto = UblParser.parse(DocumentDirection.INCOMING, xml);

        assertThat(dto.amountInclVat()).isEqualByComparingTo("100.00");
        assertThat(dto.amountExclVat()).isEqualByComparingTo("82.64");
    }

    @Test
    void picksCurrencyFromPayableAmountEvenIfOthersDiffer() throws Exception {
        // Mismatched currencyIDs — prefer the one tied to PayableAmount.
        String xml = invoiceWithMonetaryTotal("""
                <cbc:TaxExclusiveAmount currencyID="USD">82.64</cbc:TaxExclusiveAmount>
                <cbc:TaxInclusiveAmount currencyID="USD">100.00</cbc:TaxInclusiveAmount>
                <cbc:PayableAmount currencyID="EUR">100.00</cbc:PayableAmount>
                """);

        UblDto dto = UblParser.parse(DocumentDirection.INCOMING, xml);

        assertThat(dto.currency()).isEqualTo(Currency.getInstance("EUR"));
        assertThat(dto.amountInclVat()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(dto.amountExclVat()).isEqualByComparingTo(new BigDecimal("82.64"));
    }
}

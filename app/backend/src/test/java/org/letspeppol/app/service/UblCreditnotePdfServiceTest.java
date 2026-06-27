package org.letspeppol.app.service;

import com.helger.ubl21.UBL21Marshaller;
import lombok.SneakyThrows;
import oasis.names.specification.ubl.schema.xsd.creditnote_21.CreditNoteType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.util.CreditNoteUBLBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class UblCreditnotePdfServiceTest {

    @SneakyThrows
    @Test
    void creditNotePdf() {
        CreditNoteUBLBuilder ublBuilder = new CreditNoteUBLBuilder();
        CreditNoteType creditNoteType = ublBuilder.buildCreditNote();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        UBL21Marshaller.creditNote().write(creditNoteType, byteArrayOutputStream);


        UblInvoicePdfService sut = new UblInvoicePdfService(null);
        byte[] pdf = sut.toPdf(byteArrayOutputStream.toString(StandardCharsets.UTF_8));

        Files.createDirectories(Paths.get("build", "debug"));
        Files.write(Paths.get("build", "debug", "creditnote.pdf"), pdf);
    }

    @SneakyThrows
    @Test
    void creditNoteDraftModeOmitsNumberAndAddsWatermark() {
        String xml = """
                <CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"
                            xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                            xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>CN-SECRET</cbc:ID>
                    <cbc:IssueDate>2026-01-05</cbc:IssueDate>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>

                    <cac:AccountingSupplierParty>
                        <cac:Party>
                            <cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName>
                        </cac:Party>
                    </cac:AccountingSupplierParty>

                    <cac:AccountingCustomerParty>
                        <cac:Party>
                            <cac:PartyName><cbc:Name>Customer BV</cbc:Name></cac:PartyName>
                        </cac:Party>
                    </cac:AccountingCustomerParty>

                    <cac:LegalMonetaryTotal>
                        <cbc:PayableAmount currencyID="EUR">-12.34</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </CreditNote>
                """;

        UblInvoicePdfService sut = new UblInvoicePdfService(null);
        byte[] pdf = sut.toPdf(xml, UblInvoicePdfService.RenderMode.DRAFT);

        String pdfText = extractText(pdf);
        assertTrue(pdfText.contains("DRAFT"));
        assertFalse(pdfText.contains("CN-SECRET"));
    }

    @SneakyThrows
    @Test
    void creditNoteZeroVatLinesRenderReasonFootnotes() {
        String xml = """
                <CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"
                            xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                            xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>CN-FOOTNOTE</cbc:ID>
                    <cbc:IssueDate>2026-01-05</cbc:IssueDate>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>

                    <cac:AccountingSupplierParty>
                        <cac:Party>
                            <cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName>
                        </cac:Party>
                    </cac:AccountingSupplierParty>

                    <cac:AccountingCustomerParty>
                        <cac:Party>
                            <cac:PartyName><cbc:Name>Customer BV</cbc:Name></cac:PartyName>
                        </cac:Party>
                    </cac:AccountingCustomerParty>

                    <cac:CreditNoteLine>
                        <cbc:ID>1</cbc:ID>
                        <cbc:CreditedQuantity unitCode="H87">1</cbc:CreditedQuantity>
                        <cbc:LineExtensionAmount currencyID="EUR">100.00</cbc:LineExtensionAmount>
                        <cac:Item>
                            <cbc:Name>Credit for consulting services</cbc:Name>
                            <cac:ClassifiedTaxCategory>
                                <cbc:ID>AE</cbc:ID>
                                <cbc:Percent>0</cbc:Percent>
                                <cbc:TaxExemptionReasonCode>VATEX-EU-AE</cbc:TaxExemptionReasonCode>
                                <cbc:TaxExemptionReason>due to article 44</cbc:TaxExemptionReason>
                                <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                            </cac:ClassifiedTaxCategory>
                        </cac:Item>
                        <cac:Price>
                            <cbc:PriceAmount currencyID="EUR">100.00</cbc:PriceAmount>
                        </cac:Price>
                    </cac:CreditNoteLine>

                    <cac:TaxTotal>
                        <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                    </cac:TaxTotal>

                    <cac:LegalMonetaryTotal>
                        <cbc:TaxExclusiveAmount currencyID="EUR">100.00</cbc:TaxExclusiveAmount>
                        <cbc:PayableAmount currencyID="EUR">100.00</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </CreditNote>
                """;

        UblInvoicePdfService sut = new UblInvoicePdfService(null);
        byte[] pdf = sut.toPdf(xml);

        Files.createDirectories(Paths.get("build", "debug"));
        Files.write(Paths.get("build", "debug", "creditnote-zero-vat-footnote.pdf"), pdf);

        String pdfText = extractText(pdf);
        assertTrue(pdfText.contains("0% VAT notes"), pdfText);
        assertTrue(pdfText.contains("Reverse Charge"), pdfText);
        assertTrue(pdfText.contains("due to article 44"), pdfText);
    }

    private static String extractText(byte[] pdf) throws java.io.IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

}

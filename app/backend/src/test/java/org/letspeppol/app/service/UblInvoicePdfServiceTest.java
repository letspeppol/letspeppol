package org.letspeppol.app.service;

import com.helger.ubl21.UBL21Marshaller;
import lombok.SneakyThrows;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.util.InvoiceUBLBuilder;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class UblInvoicePdfServiceTest {

    @SneakyThrows
    @Test
    void invoicePdf() {
        InvoiceUBLBuilder ublBuilder = new InvoiceUBLBuilder();
        InvoiceType invoiceType = ublBuilder.buildInvoice();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        UBL21Marshaller.invoice().write(invoiceType, byteArrayOutputStream);

        UblInvoicePdfService sut = new UblInvoicePdfService(null);
        byte[] pdf = sut.toPdf(byteArrayOutputStream.toString(StandardCharsets.UTF_8));

        Files.createDirectories(Paths.get("build", "debug"));
        Files.write(Paths.get("build", "debug", "invoice.pdf"), pdf);
        Files.write(Paths.get("build", "debug", "invoice.xml"), byteArrayOutputStream.toByteArray());
    }

    @SneakyThrows
    @Test
    void draftModeOmitsInvoiceNumberAndAddsWatermark() {
        UblInvoicePdfService sut = new UblInvoicePdfService(null);
        byte[] pdf = sut.toPdf(xml, UblInvoicePdfService.RenderMode.DRAFT);

        assertNotNull(pdf);
        assertTrue(pdf.length > 500);

        Files.createDirectories(Paths.get("build", "debug"));
        Files.write(Paths.get("build", "debug", "invoice-draft.pdf"), pdf);

        String pdfText = extractText(pdf);
        assertTrue(pdfText.contains("DRAFT"), "PDF should contain watermark text 'DRAFT'");
        assertFalse(pdfText.contains("INV-SECRET"), "Invoice number must not be present in draft PDFs");
    }

    @SneakyThrows
    @Test
    void proformaModeOmitsInvoiceNumberAndAddsWatermark() {
        UblInvoicePdfService sut = new UblInvoicePdfService(null);
        byte[] pdf = sut.toPdf(xml, UblInvoicePdfService.RenderMode.PROFORMA);

        assertNotNull(pdf);
        assertTrue(pdf.length > 500);

        Files.createDirectories(Paths.get("build", "debug"));
        Files.write(Paths.get("build", "debug", "invoice-proforma.pdf"), pdf);

        String pdfText = extractText(pdf);
        assertTrue(pdfText.contains("PROFORMA"), "PDF should contain watermark text 'PROFORMA'");
        assertFalse(pdfText.contains("INV-SECRET"), "Invoice number must not be present in proforma PDFs");
    }

    @SneakyThrows
    @Test
    void zeroVatLinesRenderReasonFootnotes() {
        String xml = """
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>INV-FOOTNOTE</cbc:ID>
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

                    <cac:InvoiceLine>
                        <cbc:ID>1</cbc:ID>
                        <cbc:InvoicedQuantity unitCode="H87">1</cbc:InvoicedQuantity>
                        <cbc:LineExtensionAmount currencyID="EUR">100.00</cbc:LineExtensionAmount>
                        <cac:Item>
                            <cbc:Name>Consulting services</cbc:Name>
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
                    </cac:InvoiceLine>

                    <cac:TaxTotal>
                        <cbc:TaxAmount currencyID="EUR">0.00</cbc:TaxAmount>
                    </cac:TaxTotal>

                    <cac:LegalMonetaryTotal>
                        <cbc:TaxExclusiveAmount currencyID="EUR">100.00</cbc:TaxExclusiveAmount>
                        <cbc:PayableAmount currencyID="EUR">100.00</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </Invoice>
                """;

        UblInvoicePdfService sut = new UblInvoicePdfService(null);
        byte[] pdf = sut.toPdf(xml);

        Files.createDirectories(Paths.get("build", "debug"));
        Files.write(Paths.get("build", "debug", "invoice-zero-vat-footnote.pdf"), pdf);

        String pdfText = extractText(pdf);
        assertTrue(pdfText.contains("0% VAT notes"), pdfText);
        assertTrue(pdfText.contains("Reverse Charge"), pdfText);
        assertTrue(pdfText.contains("due to article 44"), pdfText);
    }

    private static String extractText(byte[] pdf) throws java.io.IOException {
        // Raw byte scanning misses the watermark because PDF text streams use FlateDecode.
        try (PDDocument doc = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    // Minimal invoice-like XML with the UBL elements we use in the XSLT (namespace-agnostic via local-name()).
    String xml = """
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>INV-1</cbc:ID>
                    <cbc:IssueDate>2026-01-05</cbc:IssueDate>
                    <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>

                    <cac:AccountingSupplierParty>
                        <cac:Party>
                            <cbc:EndpointID schemeID="0208">123456789</cbc:EndpointID>
                            <cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName>
                        </cac:Party>
                    </cac:AccountingSupplierParty>

                    <cac:AccountingCustomerParty>
                        <cac:Party>
                            <cbc:EndpointID schemeID="0208">987654321</cbc:EndpointID>
                            <cac:PartyName><cbc:Name>Customer BV</cbc:Name></cac:PartyName>
                        </cac:Party>
                    </cac:AccountingCustomerParty>

                    <cac:LegalMonetaryTotal>
                        <cbc:PayableAmount currencyID="EUR">12.34</cbc:PayableAmount>
                    </cac:LegalMonetaryTotal>
                </Invoice>
                """;
}

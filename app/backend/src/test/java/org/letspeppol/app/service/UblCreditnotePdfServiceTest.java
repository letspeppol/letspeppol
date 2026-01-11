package org.letspeppol.app.service;

import com.helger.ubl21.UBL21Marshaller;
import lombok.SneakyThrows;
import oasis.names.specification.ubl.schema.xsd.creditnote_21.CreditNoteType;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.util.CreditNoteUBLBuilder;
import org.letspeppol.app.util.InvoiceUBLBuilder;

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


        UblInvoicePdfService sut = new UblInvoicePdfService();
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

        UblInvoicePdfService sut = new UblInvoicePdfService();
        byte[] pdf = sut.toPdf(xml, UblInvoicePdfService.RenderMode.DRAFT);

        String pdfText = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(pdfText.contains("DRAFT"));
        assertFalse(pdfText.contains("CN-SECRET"));
    }

}

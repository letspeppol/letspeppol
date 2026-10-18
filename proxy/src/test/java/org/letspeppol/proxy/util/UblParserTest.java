package org.letspeppol.proxy.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.letspeppol.proxy.model.DocumentType;

import static org.assertj.core.api.Assertions.assertThat;

class UblParserTest {
    @Test
    void classifiesXmlWithALeadingUtf8Bom() throws Exception {
        String xml = "\uFEFF<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">"
                + "<InvoiceTypeCode>389</InvoiceTypeCode></Invoice>";
        assertThat(UblParser.parseDocumentType(xml)).isEqualTo(DocumentType.SELF_BILLING_INVOICE);
    }

    @ParameterizedTest
    @CsvSource({"Invoice, InvoiceTypeCode, 380, false", "Invoice, InvoiceTypeCode, 389, true",
            "Invoice, InvoiceTypeCode, 527, true", "CreditNote, CreditNoteTypeCode, 381, false",
            "CreditNote, CreditNoteTypeCode, 261, true"})
    void resolvesTransportSenderAndReceiver(String root, String type, String code, boolean selfBilling) throws Exception {
        String xml = """
                <%s xmlns="urn:oasis:names:specification:ubl:schema:xsd:%s-2"
                    xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                    xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:%s>%s</cbc:%s>
                    <cac:AccountingSupplierParty><cac:Party><cbc:EndpointID schemeID="0208">supplier</cbc:EndpointID></cac:Party></cac:AccountingSupplierParty>
                    <cac:AccountingCustomerParty><cac:Party><cbc:EndpointID schemeID="0208">customer</cbc:EndpointID></cac:Party></cac:AccountingCustomerParty>
                </%s>
                """.formatted(root, root, type, code, type, root);
        var parties = UblParser.parsePeppolParties(xml);
        DocumentType parsedType = UblParser.parseDocumentType(xml);
        assertThat(parsedType).isEqualTo(root.equals("Invoice")
                ? (selfBilling ? DocumentType.SELF_BILLING_INVOICE : DocumentType.INVOICE)
                : (selfBilling ? DocumentType.SELF_BILLING_CREDIT_NOTE : DocumentType.CREDIT_NOTE));
        assertThat(parties.sender()).isEqualTo(selfBilling ? "0208:customer" : "0208:supplier");
        assertThat(parties.receiver()).isEqualTo(selfBilling ? "0208:supplier" : "0208:customer");
    }
}

package org.letspeppol.proxy.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationServiceTest {
    @ParameterizedTest
    @CsvSource({"Invoice, InvoiceTypeCode, 389, invoice-self-billing", "CreditNote, CreditNoteTypeCode, 261, creditnote-self-billing"})
    void validatesUploadedSelfBillingWithItsOwnRules(String root, String typeCode, String code, String rules) {
        String xml = """
                <%s xmlns="urn:oasis:names:specification:ubl:schema:xsd:%s-2"
                    xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:CustomizationID>urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:selfbilling:3.0</cbc:CustomizationID>
                    <cbc:ProfileID>urn:fdc:peppol.eu:2017:poacc:selfbilling:01:1.0</cbc:ProfileID>
                    <cbc:%s>%s</cbc:%s>
                </%s>
                """.formatted(root, root, typeCode, code, typeCode, root);
        var result = new ValidationService().validateUblXml(xml);
        assertThat(result.vesId()).contains("ArtifactID=" + rules + ";");
        // Selecting self-billing rules must still validate required invoice content.
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }
}

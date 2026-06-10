package org.letspeppol.app.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UblVatReasonExtractorTest {

    @Test
    void extractsVatReasonsFromInvoiceLines() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                  <cbc:ID>INV-1</cbc:ID>
                  <cac:AccountingSupplierParty><cac:Party><cbc:EndpointID schemeID="0208">123</cbc:EndpointID></cac:Party></cac:AccountingSupplierParty>
                  <cac:AccountingCustomerParty><cac:Party><cbc:EndpointID schemeID="0208">456</cbc:EndpointID></cac:Party></cac:AccountingCustomerParty>
                  <cac:InvoiceLine>
                    <cbc:ID>1</cbc:ID>
                    <cac:Item>
                      <cbc:Name>Line 1</cbc:Name>
                      <cac:ClassifiedTaxCategory>
                        <cbc:ID>E</cbc:ID>
                        <cbc:Percent>0</cbc:Percent>
                        <cbc:TaxExemptionReason>VAT exempt by Art. 44 - Enterprises without VAT</cbc:TaxExemptionReason>
                        <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                      </cac:ClassifiedTaxCategory>
                    </cac:Item>
                  </cac:InvoiceLine>
                  <cac:InvoiceLine>
                    <cbc:ID>2</cbc:ID>
                    <cac:Item>
                      <cbc:Name>Line 2</cbc:Name>
                      <cac:ClassifiedTaxCategory>
                        <cbc:ID>AE</cbc:ID>
                        <cbc:Percent>0</cbc:Percent>
                        <cbc:TaxExemptionReason>Reverse charge for subcontracting</cbc:TaxExemptionReason>
                        <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
                      </cac:ClassifiedTaxCategory>
                    </cac:Item>
                  </cac:InvoiceLine>
                </Invoice>
                """;

        List<UblVatReasonExtractor.VatReasonUsage> usages = UblVatReasonExtractor.extract(xml);

        assertEquals(2, usages.size());
        assertEquals("E", usages.get(0).selectedTaxCategoryId());
        assertEquals("VAT exempt by Art. 44 - Enterprises without VAT", usages.get(0).writtenReason());
        assertEquals("AE", usages.get(1).selectedTaxCategoryId());
        assertEquals("Reverse charge for subcontracting", usages.get(1).writtenReason());
    }
}

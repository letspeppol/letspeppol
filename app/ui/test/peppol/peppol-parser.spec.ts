import {describe, expect, test} from "vitest";
import {buildCreditNote, buildInvoice, parseCreditNote, parseInvoice} from "../../src/services/peppol/ubl-parser";

function normalizeXml(xml: string) {
    return xml
        // remove stray spaces after '=' before quotes
        .replace(/=\s+"/g, '="')
        .replace(/>(\d+)\.0</g, '>$1<')
        .replace(/\s+/g, " ")
        .trim();
}

describe("Invoice XML round-trip", () => {
    test("parse -> build should match original XML", () => {
        // Parse XML to Invoice object
        const invoiceObj = parseInvoice(sampleInvoiceXml);

        expect(invoiceObj.CustomizationID).toBe(
            "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0"
        );
        expect(invoiceObj.ProfileID).toBe(
            "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0"
        );
        expect(invoiceObj.ID).toBe("Snippet1");
        expect(invoiceObj.IssueDate).toBe("2025-11-13");
        expect(invoiceObj.DueDate).toBe("2025-12-01");
        expect(invoiceObj.InvoiceTypeCode).toBe(380);
        expect(invoiceObj.DocumentCurrencyCode).toBe("EUR");
        expect(invoiceObj.AccountingCost).toBe("4025:123:4343");
        expect(invoiceObj.BuyerReference).toBe("0150abc");

        // --- Supplier ---
        const supplier = invoiceObj.AccountingSupplierParty.Party;
        expect(supplier.EndpointID?.value).toBe("1023290711");
        expect(supplier.PartyIdentification?.[0].ID.value).toBe("1023290711");
        expect(supplier.PartyName?.Name).toBe("SupplierTradingName Ltd.");
        expect(supplier.PostalAddress?.StreetName).toBe("Main street 1");
        expect(supplier.PostalAddress?.CityName).toBe("London");
        expect(supplier.PostalAddress?.PostalZone).toBe("GB 123 EW");
        expect(supplier.PartyTaxScheme?.CompanyID.value).toBe("GB1232434");
        expect(supplier.PartyLegalEntity?.RegistrationName).toBe(
            "SupplierOfficialName Ltd"
        );

        // --- Customer ---
        const customer = invoiceObj.AccountingCustomerParty.Party;
        expect(customer.EndpointID?.value).toBe("0705969661");
        expect(customer.PartyIdentification?.[0].ID.value).toBe("0705969661");
        expect(customer.PartyName?.Name).toBe("BuyerTradingName AS");
        expect(customer.PostalAddress?.StreetName).toBe("Hovedgatan 32");
        expect(customer.PostalAddress?.CityName).toBe("Stockholm");
        expect(customer.PostalAddress?.PostalZone).toBe("456 34");
        expect(customer.PartyTaxScheme?.CompanyID.value).toBe("SE4598375937");
        expect(customer.PartyLegalEntity?.CompanyID.value).toBe("39937423947");
        expect(customer.Contact?.Name).toBe("Lisa Johnson");
        expect(customer.Contact?.Telephone).toBe("23434234");
        expect(customer.Contact?.ElectronicMail).toBe("lj@buyer.se");

        // --- Delivery ---
        expect(invoiceObj.Delivery?.ActualDeliveryDate).toBe("2025-11-01");
        expect(invoiceObj.Delivery?.DeliveryLocation?.ID.value).toBe(
            "9483759475923478"
        );
        expect(invoiceObj.Delivery?.DeliveryLocation?.Address?.StreetName).toBe(
            "Delivery street 2"
        );
        expect(invoiceObj.Delivery?.DeliveryParty?.PartyName?.Name).toBe(
            "Delivery party Name"
        );

        // --- PaymentMeans ---
        expect(invoiceObj.PaymentMeans?.PaymentMeansCode?.value).toBe(30); // automatica data conversion?
        expect(invoiceObj.PaymentMeans?.PaymentMeansCode?.__name).toBe(
            "Credit transfer"
        );
        expect(invoiceObj.PaymentMeans?.PaymentID).toBe("Snippet1");
        expect(invoiceObj.PaymentMeans?.PayeeFinancialAccount?.ID).toBe(
            "IBAN32423940"
        );
        expect(invoiceObj.PaymentMeans?.PayeeFinancialAccount?.Name).toBe(
            "AccountName"
        );

        // --- PaymentTerms ---
        expect(invoiceObj.PaymentTerms?.Note).toBe(
            "Payment within 10 days, 2% discount"
        );

        // --- TaxTotal ---
        const taxTotal = invoiceObj.TaxTotal?.[0];
        expect(taxTotal?.TaxAmount.value).toBe(331.25);
        const taxSubtotal = taxTotal?.TaxSubtotal?.[0];
        expect(taxSubtotal?.TaxableAmount.value).toBe(1325);
        expect(taxSubtotal?.TaxAmount.value).toBe(331.25);
        expect(taxSubtotal?.TaxCategory?.Percent).toBe(25);
        expect(taxSubtotal?.TaxCategory?.TaxScheme?.ID).toBe("VAT");

        // --- LegalMonetaryTotal ---
        const monetaryTotal = invoiceObj.LegalMonetaryTotal;
        expect(monetaryTotal.LineExtensionAmount?.value).toBe(1300);
        expect(monetaryTotal.TaxExclusiveAmount?.value).toBe(1325);
        expect(monetaryTotal.TaxInclusiveAmount?.value).toBe(1656.25);
        expect(monetaryTotal.ChargeTotalAmount?.value).toBe(25);
        expect(monetaryTotal.PayableAmount.value).toBe(1656.25);

        // --- InvoiceLine ---
        expect(invoiceObj.InvoiceLine.length).toBe(1);
        const line = invoiceObj.InvoiceLine[0];
        expect(line.ID).toBe("1");
        expect(line.InvoicedQuantity?.value).toBe(7);
        expect(line.LineExtensionAmount.value).toBe(2800);
        expect(line.Item.Name).toBe("item name");
        expect(line.Item.Description).toBe("Description of item");
        expect(line.Item.StandardItemIdentification?.ID.value).toBe(
            "21382183120983"
        );
        expect(line.Item.OriginCountry?.IdentificationCode).toBe("NO");
        expect(line.Item.ClassifiedTaxCategory?.Percent).toBe(25);
        expect(line.Price.PriceAmount.value).toBe(400);

        // --- Attachment ---
        expect(invoiceObj.AdditionalDocumentReference.length).toBe(1);
        const additionalDocumentReference = invoiceObj.AdditionalDocumentReference[0];
        expect(additionalDocumentReference.ID).toBe("ATTACHMENT-099");
        expect(additionalDocumentReference.Attachment.EmbeddedDocumentBinaryObject.__mimeCode).toBe("application/text");
        expect(additionalDocumentReference.Attachment.EmbeddedDocumentBinaryObject.__filename).toBe("ATTACHMENT-099.txt");
        expect(additionalDocumentReference.Attachment.EmbeddedDocumentBinaryObject.value).toBe("RGl0IGlzIGVlbiB0ZXN0");

        // Build XML from Invoice object
        const rebuiltXml = buildInvoice(invoiceObj);

        // Normalize whitespace for comparison
        const originalNormalized = `<?xml version="1.0" encoding="UTF-8"?>` + normalizeXml(sampleInvoiceXml);
        const rebuiltNormalized = normalizeXml(rebuiltXml);

        expect(rebuiltNormalized).toBe(originalNormalized);
    });
});

describe("CreditNote XML round-trip", () => {
    test("parse -> build should match original XML", () => {
        const creditNoteObj = parseCreditNote(sampleCreditNoteXml);

        expect(creditNoteObj.CustomizationID).toBe(
            "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0"
        );
        expect(creditNoteObj.ProfileID).toBe(
            "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0"
        );
        expect(creditNoteObj.ID).toBe("Snippet1");
        expect(creditNoteObj.IssueDate).toBe("2025-11-13");
        expect(creditNoteObj.CreditNoteTypeCode).toBe(381);
        expect(creditNoteObj.DocumentCurrencyCode).toBe("EUR");
        expect(creditNoteObj.AccountingCost).toBe("4025:123:4343");
        expect(creditNoteObj.BuyerReference).toBe("0150abc");

        // Supplier
        const supplier = creditNoteObj.AccountingSupplierParty.Party;
        expect(supplier.EndpointID?.value).toBe("1023290711");
        expect(supplier.PartyIdentification?.[0].ID.value).toBe("1023290711");
        expect(supplier.PartyName?.Name).toBe("SupplierTradingName Ltd.");
        expect(supplier.PostalAddress?.StreetName).toBe("Main street 1");
        expect(supplier.PostalAddress?.CityName).toBe("London");
        expect(supplier.PostalAddress?.PostalZone).toBe("GB 123 EW");
        expect(supplier.PartyTaxScheme?.CompanyID.value).toBe("GB1232434");
        expect(supplier.PartyLegalEntity?.RegistrationName).toBe(
            "SupplierOfficialName Ltd"
        );

        // Customer
        const customer = creditNoteObj.AccountingCustomerParty.Party;
        expect(customer.EndpointID?.value).toBe("0705969661");
        expect(customer.PartyIdentification?.[0].ID.value).toBe("0705969661");
        expect(customer.PartyName?.Name).toBe("BuyerTradingName AS");
        expect(customer.PostalAddress?.StreetName).toBe("Hovedgatan 32");
        expect(customer.PostalAddress?.CityName).toBe("Stockholm");
        expect(customer.PostalAddress?.PostalZone).toBe("456 34");
        expect(customer.PartyTaxScheme?.CompanyID.value).toBe("SE4598375937");
        expect(customer.PartyLegalEntity?.CompanyID.value).toBe("39937423947");
        expect(customer.Contact?.Name).toBe("Lisa Johnson");
        expect(customer.Contact?.Telephone).toBe("23434234");
        expect(customer.Contact?.ElectronicMail).toBe("lj@buyer.se");

        // AllowanceCharge
        const allowance = creditNoteObj.AllowanceCharge?.[0];
        expect(allowance?.ChargeIndicator).toBe(true);
        expect(allowance?.AllowanceChargeReason).toBe("Insurance");
        expect(allowance?.Amount.value).toBe(25);
        expect(allowance?.TaxCategory?.Percent).toBe(25.0);

        // TaxTotal
        const taxTotal = creditNoteObj.TaxTotal?.[0];
        expect(taxTotal?.TaxAmount.value).toBe(331.25);
        const taxSubtotal = taxTotal?.TaxSubtotal?.[0];
        expect(taxSubtotal?.TaxableAmount.value).toBe(1325);
        expect(taxSubtotal?.TaxAmount.value).toBe(331.25);
        expect(taxSubtotal?.TaxCategory?.Percent).toBe(25.0);

        // Monetary total
        const monetaryTotal = creditNoteObj.LegalMonetaryTotal;
        expect(monetaryTotal.LineExtensionAmount?.value).toBe(1300);
        expect(monetaryTotal.TaxExclusiveAmount?.value).toBe(1325);
        expect(monetaryTotal.TaxInclusiveAmount?.value).toBe(1656.25);
        expect(monetaryTotal.ChargeTotalAmount?.value).toBe(25);
        expect(monetaryTotal.PayableAmount.value).toBe(1656.25);

        // CreditNoteLine
        expect(creditNoteObj.CreditNoteLine.length).toBe(1);
        const line = creditNoteObj.CreditNoteLine[0];
        expect(line.ID).toBe("1");
        expect(line.CreditedQuantity?.value).toBe(7);
        expect(line.LineExtensionAmount.value).toBe(2800);
        expect(line.Item.Name).toBe("item name");
        expect(line.Item.Description).toBe("Description of item");
        expect(line.Item.StandardItemIdentification?.ID.value).toBe(
            "21382183120983"
        );
        expect(line.Item.OriginCountry?.IdentificationCode).toBe("NO");
        expect(line.Item.ClassifiedTaxCategory?.Percent).toBe(25.0);
        expect(line.Price.PriceAmount.value).toBe(400);

        const rebuiltXml = buildCreditNote(creditNoteObj);
        const originalNormalized = normalizeXml(sampleCreditNoteXml);
        const rebuiltNormalized = normalizeXml(rebuiltXml);
        expect(rebuiltNormalized).toBe(originalNormalized);
    });
});

const sampleCreditNoteXml =
`
<CreditNote xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
         xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2">
  <cbc:CustomizationID>urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0</cbc:CustomizationID>
  <cbc:ProfileID>urn:fdc:peppol.eu:2017:poacc:billing:01:1.0</cbc:ProfileID>
  <cbc:ID>Snippet1</cbc:ID>
  <cbc:IssueDate>2025-11-13</cbc:IssueDate>
  <cbc:CreditNoteTypeCode>381</cbc:CreditNoteTypeCode>
  <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
  <cbc:AccountingCost>4025:123:4343</cbc:AccountingCost>
  <cbc:BuyerReference>0150abc</cbc:BuyerReference>
  <cac:AccountingSupplierParty>
    <cac:Party>
      <cbc:EndpointID schemeID="0208">1023290711</cbc:EndpointID>
      <cac:PartyIdentification>
        <cbc:ID schemeID="0208">1023290711</cbc:ID>
      </cac:PartyIdentification>
      <cac:PartyName>
        <cbc:Name>SupplierTradingName Ltd.</cbc:Name>
      </cac:PartyName>
      <cac:PostalAddress>
        <cbc:StreetName>Main street 1</cbc:StreetName>
        <cbc:AdditionalStreetName>Postbox 123</cbc:AdditionalStreetName>
        <cbc:CityName>London</cbc:CityName>
        <cbc:PostalZone>GB 123 EW</cbc:PostalZone>
        <cac:Country>
          <cbc:IdentificationCode>GB</cbc:IdentificationCode>
        </cac:Country>
      </cac:PostalAddress>
      <cac:PartyTaxScheme>
        <cbc:CompanyID>GB1232434</cbc:CompanyID>
        <cac:TaxScheme>
          <cbc:ID>VAT</cbc:ID>
        </cac:TaxScheme>
      </cac:PartyTaxScheme>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>SupplierOfficialName Ltd</cbc:RegistrationName>
        <cbc:CompanyID>GB983294</cbc:CompanyID>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingSupplierParty>
  <cac:AccountingCustomerParty>
    <cac:Party>
      <cbc:EndpointID schemeID="0208">0705969661</cbc:EndpointID>
      <cac:PartyIdentification>
        <cbc:ID schemeID="0208">0705969661</cbc:ID>
      </cac:PartyIdentification>
      <cac:PartyName>
        <cbc:Name>BuyerTradingName AS</cbc:Name>
      </cac:PartyName>
      <cac:PostalAddress>
        <cbc:StreetName>Hovedgatan 32</cbc:StreetName>
        <cbc:AdditionalStreetName>Po box 878</cbc:AdditionalStreetName>
        <cbc:CityName>Stockholm</cbc:CityName>
        <cbc:PostalZone>456 34</cbc:PostalZone>
        <cac:Country>
          <cbc:IdentificationCode>SE</cbc:IdentificationCode>
        </cac:Country>
      </cac:PostalAddress>
      <cac:PartyTaxScheme>
        <cbc:CompanyID>SE4598375937</cbc:CompanyID>
        <cac:TaxScheme>
          <cbc:ID>VAT</cbc:ID>
        </cac:TaxScheme>
      </cac:PartyTaxScheme>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>Buyer Official Name</cbc:RegistrationName>
        <cbc:CompanyID schemeID="0183">39937423947</cbc:CompanyID>
      </cac:PartyLegalEntity>
      <cac:Contact>
        <cbc:Name>Lisa Johnson</cbc:Name>
        <cbc:Telephone>23434234</cbc:Telephone>
        <cbc:ElectronicMail>lj@buyer.se</cbc:ElectronicMail>
      </cac:Contact>
    </cac:Party>
  </cac:AccountingCustomerParty>
  <cac:AllowanceCharge>
    <cbc:ChargeIndicator>true</cbc:ChargeIndicator>
    <cbc:AllowanceChargeReason>Insurance</cbc:AllowanceChargeReason>
    <cbc:Amount currencyID="EUR">25</cbc:Amount>
    <cac:TaxCategory>
      <cbc:ID>S</cbc:ID>
      <cbc:Percent>25.0</cbc:Percent>
      <cac:TaxScheme>
        <cbc:ID>VAT</cbc:ID>
      </cac:TaxScheme>
    </cac:TaxCategory>
  </cac:AllowanceCharge>
  <cac:TaxTotal>
    <cbc:TaxAmount currencyID="EUR">331.25</cbc:TaxAmount>
    <cac:TaxSubtotal>
      <cbc:TaxableAmount currencyID="EUR">1325</cbc:TaxableAmount>
      <cbc:TaxAmount currencyID="EUR">331.25</cbc:TaxAmount>
      <cac:TaxCategory>
        <cbc:ID>S</cbc:ID>
        <cbc:Percent>25.0</cbc:Percent>
        <cac:TaxScheme>
          <cbc:ID>VAT</cbc:ID>
        </cac:TaxScheme>
      </cac:TaxCategory>
    </cac:TaxSubtotal>
  </cac:TaxTotal>
  <cac:LegalMonetaryTotal>
    <cbc:LineExtensionAmount currencyID="EUR">1300</cbc:LineExtensionAmount>
    <cbc:TaxExclusiveAmount currencyID="EUR">1325</cbc:TaxExclusiveAmount>
    <cbc:TaxInclusiveAmount currencyID="EUR">1656.25</cbc:TaxInclusiveAmount>
    <cbc:ChargeTotalAmount currencyID="EUR">25</cbc:ChargeTotalAmount>
    <cbc:PayableAmount currencyID="EUR">1656.25</cbc:PayableAmount>
  </cac:LegalMonetaryTotal>

  <cac:CreditNoteLine>
    <cbc:ID>1</cbc:ID>
    <cbc:CreditedQuantity unitCode="DAY">7</cbc:CreditedQuantity>
    <cbc:LineExtensionAmount currencyID= "EUR">2800</cbc:LineExtensionAmount>
    <cbc:AccountingCost>Konteringsstreng</cbc:AccountingCost>
    <cac:OrderLineReference>
      <cbc:LineID>123</cbc:LineID>
    </cac:OrderLineReference>
    <cac:Item>
      <cbc:Description>Description of item</cbc:Description>
      <cbc:Name>item name</cbc:Name>
      <cac:StandardItemIdentification>
        <cbc:ID schemeID="0088">21382183120983</cbc:ID>
      </cac:StandardItemIdentification>
      <cac:OriginCountry>
        <cbc:IdentificationCode>NO</cbc:IdentificationCode>
      </cac:OriginCountry>
      <cac:CommodityClassification>
        <cbc:ItemClassificationCode listID="SRV">09348023</cbc:ItemClassificationCode>
      </cac:CommodityClassification>
      <cac:ClassifiedTaxCategory>
        <cbc:ID>S</cbc:ID>
        <cbc:Percent>25.0</cbc:Percent>
        <cac:TaxScheme>
          <cbc:ID>VAT</cbc:ID>
        </cac:TaxScheme>
      </cac:ClassifiedTaxCategory>
    </cac:Item>
    <cac:Price>
      <cbc:PriceAmount currencyID="EUR">400</cbc:PriceAmount>
    </cac:Price>
  </cac:CreditNoteLine>
</CreditNote>
`;

const sampleInvoiceXml = `
<Invoice xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
         xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2">
  <cbc:CustomizationID>urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0</cbc:CustomizationID>
  <cbc:ProfileID>urn:fdc:peppol.eu:2017:poacc:billing:01:1.0</cbc:ProfileID>
  <cbc:ID>Snippet1</cbc:ID>
  <cbc:IssueDate>2025-11-13</cbc:IssueDate>
  <cbc:DueDate>2025-12-01</cbc:DueDate>
  <cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>
  <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
  <cbc:AccountingCost>4025:123:4343</cbc:AccountingCost>
  <cbc:BuyerReference>0150abc</cbc:BuyerReference>
  <cac:AdditionalDocumentReference>
    <cbc:ID>ATTACHMENT-099</cbc:ID>
    <cac:Attachment>
      <cbc:EmbeddedDocumentBinaryObject mimeCode="application/text" filename="ATTACHMENT-099.txt">RGl0IGlzIGVlbiB0ZXN0</cbc:EmbeddedDocumentBinaryObject>
    </cac:Attachment>
  </cac:AdditionalDocumentReference>
  <cac:AccountingSupplierParty>
    <cac:Party>
      <cbc:EndpointID schemeID="0208">1023290711</cbc:EndpointID>
      <cac:PartyIdentification>
        <cbc:ID schemeID="0208">1023290711</cbc:ID>
        <cbc:ID schemeID="0203">1023290712</cbc:ID>
      </cac:PartyIdentification>
      <cac:PartyName>
        <cbc:Name>SupplierTradingName Ltd.</cbc:Name>
      </cac:PartyName>
      <cac:PostalAddress>
        <cbc:StreetName>Main street 1</cbc:StreetName>
        <cbc:AdditionalStreetName>Postbox 123</cbc:AdditionalStreetName>
        <cbc:CityName>London</cbc:CityName>
        <cbc:PostalZone>GB 123 EW</cbc:PostalZone>
        <cac:Country>
          <cbc:IdentificationCode>GB</cbc:IdentificationCode>
        </cac:Country>
      </cac:PostalAddress>
      <cac:PartyTaxScheme>
        <cbc:CompanyID>GB1232434</cbc:CompanyID>
        <cac:TaxScheme>
          <cbc:ID>VAT</cbc:ID>
        </cac:TaxScheme>
      </cac:PartyTaxScheme>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>SupplierOfficialName Ltd</cbc:RegistrationName>
        <cbc:CompanyID>GB983294</cbc:CompanyID>
      </cac:PartyLegalEntity>
    </cac:Party>
  </cac:AccountingSupplierParty>
  <cac:AccountingCustomerParty>
    <cac:Party>
      <cbc:EndpointID schemeID="0208">0705969661</cbc:EndpointID>
      <cac:PartyIdentification>
        <cbc:ID schemeID="0208">0705969661</cbc:ID>
      </cac:PartyIdentification>
      <cac:PartyName>
        <cbc:Name>BuyerTradingName AS</cbc:Name>
      </cac:PartyName>
      <cac:PostalAddress>
        <cbc:StreetName>Hovedgatan 32</cbc:StreetName>
        <cbc:AdditionalStreetName>Po box 878</cbc:AdditionalStreetName>
        <cbc:CityName>Stockholm</cbc:CityName>
        <cbc:PostalZone>456 34</cbc:PostalZone>
        <cac:Country>
          <cbc:IdentificationCode>SE</cbc:IdentificationCode>
        </cac:Country>
      </cac:PostalAddress>
      <cac:PartyTaxScheme>
        <cbc:CompanyID>SE4598375937</cbc:CompanyID>
        <cac:TaxScheme>
          <cbc:ID>VAT</cbc:ID>
        </cac:TaxScheme>
      </cac:PartyTaxScheme>
      <cac:PartyLegalEntity>
        <cbc:RegistrationName>Buyer Official Name</cbc:RegistrationName>
        <cbc:CompanyID schemeID="0183">39937423947</cbc:CompanyID>
      </cac:PartyLegalEntity>
      <cac:Contact>
        <cbc:Name>Lisa Johnson</cbc:Name>
        <cbc:Telephone>23434234</cbc:Telephone>
        <cbc:ElectronicMail>lj@buyer.se</cbc:ElectronicMail>
      </cac:Contact>
    </cac:Party>
  </cac:AccountingCustomerParty>
  <cac:Delivery>
    <cbc:ActualDeliveryDate>2025-11-01</cbc:ActualDeliveryDate>
    <cac:DeliveryLocation>
      <cbc:ID schemeID="0088">9483759475923478</cbc:ID>
      <cac:Address>
        <cbc:StreetName>Delivery street 2</cbc:StreetName>
        <cbc:AdditionalStreetName>Building 56</cbc:AdditionalStreetName>
        <cbc:CityName>Stockholm</cbc:CityName>
        <cbc:PostalZone>21234</cbc:PostalZone>
        <cac:Country>
          <cbc:IdentificationCode>SE</cbc:IdentificationCode>
        </cac:Country>
      </cac:Address>
    </cac:DeliveryLocation>
    <cac:DeliveryParty>
      <cac:PartyName>
        <cbc:Name>Delivery party Name</cbc:Name>
      </cac:PartyName>
    </cac:DeliveryParty>
  </cac:Delivery>
  <cac:PaymentMeans>
    <cbc:PaymentMeansCode name="Credit transfer">30</cbc:PaymentMeansCode>
    <cbc:PaymentID>Snippet1</cbc:PaymentID>
    <cac:PayeeFinancialAccount>
      <cbc:ID>IBAN32423940</cbc:ID>
      <cbc:Name>AccountName</cbc:Name>
      <cac:FinancialInstitutionBranch>
        <cbc:ID>BIC324098</cbc:ID>
      </cac:FinancialInstitutionBranch>
    </cac:PayeeFinancialAccount>
  </cac:PaymentMeans>
  <cac:PaymentTerms>
    <cbc:Note>Payment within 10 days, 2% discount</cbc:Note>
  </cac:PaymentTerms>
  <cac:TaxTotal>
    <cbc:TaxAmount currencyID="EUR">331.25</cbc:TaxAmount>
    <cac:TaxSubtotal>
      <cbc:TaxableAmount currencyID="EUR">1325</cbc:TaxableAmount>
      <cbc:TaxAmount currencyID="EUR">331.25</cbc:TaxAmount>
      <cac:TaxCategory>
        <cbc:ID>S</cbc:ID>
        <cbc:Percent>25</cbc:Percent>
        <cac:TaxScheme>
          <cbc:ID>VAT</cbc:ID>
        </cac:TaxScheme>
      </cac:TaxCategory>
    </cac:TaxSubtotal>
  </cac:TaxTotal>
  <cac:LegalMonetaryTotal>
  <cbc:LineExtensionAmount currencyID="EUR">1300</cbc:LineExtensionAmount>
    <cbc:TaxExclusiveAmount currencyID="EUR">1325</cbc:TaxExclusiveAmount>
    <cbc:TaxInclusiveAmount currencyID="EUR">1656.25</cbc:TaxInclusiveAmount>
    <cbc:ChargeTotalAmount currencyID="EUR">25</cbc:ChargeTotalAmount>
    <cbc:PayableAmount currencyID="EUR">1656.25</cbc:PayableAmount>
  </cac:LegalMonetaryTotal>
  <cac:InvoiceLine>
    <cbc:ID>1</cbc:ID>
    <cbc:InvoicedQuantity unitCode="DAY">7</cbc:InvoicedQuantity>
    <cbc:LineExtensionAmount currencyID="EUR">2800</cbc:LineExtensionAmount>
    <cbc:AccountingCost>Konteringsstreng</cbc:AccountingCost>
    <cac:OrderLineReference>
      <cbc:LineID>123</cbc:LineID>
    </cac:OrderLineReference>
    <cac:Item>
      <cbc:Description>Description of item</cbc:Description>
      <cbc:Name>item name</cbc:Name>
      <cac:StandardItemIdentification>
        <cbc:ID schemeID="0088">21382183120983</cbc:ID>
      </cac:StandardItemIdentification>
      <cac:OriginCountry>
        <cbc:IdentificationCode>NO</cbc:IdentificationCode>
      </cac:OriginCountry>
      <cac:CommodityClassification>
        <cbc:ItemClassificationCode listID="SRV">09348023</cbc:ItemClassificationCode>
      </cac:CommodityClassification>
      <cac:ClassifiedTaxCategory>
        <cbc:ID>S</cbc:ID>
        <cbc:Percent>25</cbc:Percent>
        <cac:TaxScheme>
          <cbc:ID>VAT</cbc:ID>
        </cac:TaxScheme>
      </cac:ClassifiedTaxCategory>
    </cac:Item>
    <cac:Price>
      <cbc:PriceAmount currencyID="EUR">400</cbc:PriceAmount>
    </cac:Price>
  </cac:InvoiceLine>
</Invoice>
`;

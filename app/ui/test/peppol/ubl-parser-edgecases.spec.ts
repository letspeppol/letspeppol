import { describe, expect, test } from 'vitest';
import { parseInvoice, parseCreditNote } from '../../src/services/peppol/ubl-parser';

// Minimal helper to wrap an arbitrary fragment in an Invoice or CreditNote root
function wrapInvoice(body: string): string {
    return `<?xml version="1.0" encoding="UTF-8"?>\n<Invoice xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">${body}</Invoice>`;
}

function wrapCreditNote(body: string): string {
    return `<?xml version="1.0" encoding="UTF-8"?>\n<CreditNote xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">${body}</CreditNote>`;
}

describe('UBL parser edge cases', () => {
    test('PartyIdentification is always an array, single and multiple', () => {
        const singleXml = wrapInvoice(`
          <cac:AccountingSupplierParty>
            <cac:Party>
              <cac:PartyIdentification>
                <cbc:ID schemeID="0208">123</cbc:ID>
              </cac:PartyIdentification>
            </cac:Party>
          </cac:AccountingSupplierParty>
          <cbc:CustomizationID>c</cbc:CustomizationID>
          <cbc:ProfileID>p</cbc:ProfileID>
          <cbc:ID>id</cbc:ID>
          <cbc:IssueDate>2020-01-01</cbc:IssueDate>
          <cac:BillingReference><cac:InvoiceDocumentReference><cbc:ID>1</cbc:ID></cac:InvoiceDocumentReference></cac:BillingReference>
          <cac:AccountingCustomerParty><cac:Party/></cac:AccountingCustomerParty>
          <cac:LegalMonetaryTotal><cbc:PayableAmount currencyID="EUR">0</cbc:PayableAmount></cac:LegalMonetaryTotal>
        `);

        const multiXml = wrapInvoice(`
          <cac:AccountingSupplierParty>
            <cac:Party>
              <cac:PartyIdentification>
                <cbc:ID schemeID="0208">123</cbc:ID>
              </cac:PartyIdentification>
              <cac:PartyIdentification>
                <cbc:ID schemeID="0208">456</cbc:ID>
              </cac:PartyIdentification>
            </cac:Party>
          </cac:AccountingSupplierParty>
          <cbc:CustomizationID>c</cbc:CustomizationID>
          <cbc:ProfileID>p</cbc:ProfileID>
          <cbc:ID>id</cbc:ID>
          <cbc:IssueDate>2020-01-01</cbc:IssueDate>
          <cac:BillingReference><cac:InvoiceDocumentReference><cbc:ID>1</cbc:ID></cac:InvoiceDocumentReference></cac:BillingReference>
          <cac:AccountingCustomerParty><cac:Party/></cac:AccountingCustomerParty>
          <cac:LegalMonetaryTotal><cbc:PayableAmount currencyID="EUR">0</cbc:PayableAmount></cac:LegalMonetaryTotal>
        `);

        const single = parseInvoice(singleXml);
        const singleIds = single.AccountingSupplierParty.Party.PartyIdentification;
        expect(Array.isArray(singleIds)).toBe(true);
        expect(singleIds).toHaveLength(1);
        expect(singleIds[0].ID.value).toBe('123');
        expect(singleIds[0].ID.__schemeID).toBe('0208');

        const multi = parseInvoice(multiXml);
        const multiIds = multi.AccountingSupplierParty.Party.PartyIdentification;
        expect(Array.isArray(multiIds)).toBe(true);
        expect(multiIds).toHaveLength(2);
        expect(multiIds[0].ID.value).toBe('123');
        expect(multiIds[1].ID.value).toBe('456');
    });

    test('PartyLegalEntity.CompanyID normalization', () => {
        const xml = wrapInvoice(`
          <cac:AccountingSupplierParty>
            <cac:Party>
              <cac:PartyLegalEntity>
                <cbc:RegistrationName>Acme</cbc:RegistrationName>
                <cbc:CompanyID schemeID="0183">123456789</cbc:CompanyID>
              </cac:PartyLegalEntity>
            </cac:Party>
          </cac:AccountingSupplierParty>
          <cbc:CustomizationID>c</cbc:CustomizationID>
          <cbc:ProfileID>p</cbc:ProfileID>
          <cbc:ID>id</cbc:ID>
          <cbc:IssueDate>2020-01-01</cbc:IssueDate>
          <cac:BillingReference><cac:InvoiceDocumentReference><cbc:ID>1</cbc:ID></cac:InvoiceDocumentReference></cac:BillingReference>
          <cac:AccountingCustomerParty><cac:Party/></cac:AccountingCustomerParty>
          <cac:LegalMonetaryTotal><cbc:PayableAmount currencyID="EUR">0</cbc:PayableAmount></cac:LegalMonetaryTotal>
        `);

        const invoice = parseInvoice(xml);
        const ple = invoice.AccountingSupplierParty.Party.PartyLegalEntity;
        expect(ple.CompanyID.value).toBe('123456789');
        expect(ple.CompanyID.__schemeID).toBe('0183');
    });

    test('single-item tax and line collections become arrays', () => {
        const xml = wrapInvoice(`
          <cac:TaxTotal>
            <cbc:TaxAmount currencyID="EUR">10</cbc:TaxAmount>
          </cac:TaxTotal>
          <cac:AllowanceCharge>
            <cbc:ChargeIndicator>true</cbc:ChargeIndicator>
            <cbc:Amount currencyID="EUR">5</cbc:Amount>
          </cac:AllowanceCharge>
          <cac:InvoiceLine>
            <cbc:ID>1</cbc:ID>
            <cbc:LineExtensionAmount currencyID="EUR">10</cbc:LineExtensionAmount>
            <cac:Item><cbc:Name>Item</cbc:Name></cac:Item>
            <cac:Price><cbc:PriceAmount currencyID="EUR">10</cbc:PriceAmount></cac:Price>
          </cac:InvoiceLine>
          <cac:AdditionalDocumentReference>
            <cbc:ID>doc1</cbc:ID>
          </cac:AdditionalDocumentReference>
          <cbc:CustomizationID>c</cbc:CustomizationID>
          <cbc:ProfileID>p</cbc:ProfileID>
          <cbc:ID>id</cbc:ID>
          <cbc:IssueDate>2020-01-01</cbc:IssueDate>
          <cac:BillingReference><cac:InvoiceDocumentReference><cbc:ID>1</cbc:ID></cac:InvoiceDocumentReference></cac:BillingReference>
          <cac:AccountingSupplierParty><cac:Party/></cac:AccountingSupplierParty>
          <cac:AccountingCustomerParty><cac:Party/></cac:AccountingCustomerParty>
          <cac:LegalMonetaryTotal><cbc:PayableAmount currencyID="EUR">0</cbc:PayableAmount></cac:LegalMonetaryTotal>
        `);

        const invoice = parseInvoice(xml);
        expect(Array.isArray(invoice.TaxTotal)).toBe(true);
        expect(Array.isArray(invoice.AllowanceCharge)).toBe(true);
        expect(Array.isArray(invoice.InvoiceLine)).toBe(true);
        expect(Array.isArray(invoice.AdditionalDocumentReference)).toBe(true);
    });

    test('attribute mapping for PaymentMeansCode and EmbeddedDocumentBinaryObject', () => {
        const invoiceXml = wrapInvoice(`
          <cac:PaymentMeans>
            <cbc:PaymentMeansCode name="Credit transfer">30</cbc:PaymentMeansCode>
          </cac:PaymentMeans>
          <cac:AdditionalDocumentReference>
            <cbc:ID>doc</cbc:ID>
            <cac:Attachment>
              <cbc:EmbeddedDocumentBinaryObject mimeCode="text/plain" filename="x.txt">data</cbc:EmbeddedDocumentBinaryObject>
            </cac:Attachment>
          </cac:AdditionalDocumentReference>
          <cbc:CustomizationID>c</cbc:CustomizationID>
          <cbc:ProfileID>p</cbc:ProfileID>
          <cbc:ID>id</cbc:ID>
          <cbc:IssueDate>2020-01-01</cbc:IssueDate>
          <cac:BillingReference><cac:InvoiceDocumentReference><cbc:ID>1</cbc:ID></cac:InvoiceDocumentReference></cac:BillingReference>
          <cac:AccountingSupplierParty><cac:Party/></cac:AccountingSupplierParty>
          <cac:AccountingCustomerParty><cac:Party/></cac:AccountingCustomerParty>
          <cac:LegalMonetaryTotal><cbc:PayableAmount currencyID="EUR">0</cbc:PayableAmount></cac:LegalMonetaryTotal>
        `);

        const invoice = parseInvoice(invoiceXml);
        const pmc = invoice.PaymentMeans.PaymentMeansCode;
        expect(pmc.value).toBe(30);
        expect(pmc.__name).toBe('Credit transfer');

        const doc = invoice.AdditionalDocumentReference[0];
        const edo = doc.Attachment.EmbeddedDocumentBinaryObject;
        expect(edo.value).toBe('data');
        expect(edo.__mimeCode).toBe('text/plain');
        expect(edo.__filename).toBe('x.txt');
    });

    test('non-numeric value remains string', () => {
        const creditNoteXml = wrapCreditNote(`
          <cac:CreditNoteLine>
            <cbc:ID>1</cbc:ID>
            <cbc:LineExtensionAmount currencyID="EUR">N/A</cbc:LineExtensionAmount>
            <cac:Item><cbc:Name>Item</cbc:Name></cac:Item>
            <cac:Price><cbc:PriceAmount currencyID="EUR">10</cbc:PriceAmount></cac:Price>
          </cac:CreditNoteLine>
          <cbc:CustomizationID>c</cbc:CustomizationID>
          <cbc:ProfileID>p</cbc:ProfileID>
          <cbc:ID>id</cbc:ID>
          <cbc:IssueDate>2020-01-01</cbc:IssueDate>
          <cac:BillingReference><cac:InvoiceDocumentReference><cbc:ID>1</cbc:ID></cac:InvoiceDocumentReference></cac:BillingReference>
          <cac:AccountingSupplierParty><cac:Party/></cac:AccountingSupplierParty>
          <cac:AccountingCustomerParty><cac:Party/></cac:AccountingCustomerParty>
          <cac:LegalMonetaryTotal><cbc:PayableAmount currencyID="EUR">0</cbc:PayableAmount></cac:LegalMonetaryTotal>
        `);

        const credit = parseCreditNote(creditNoteXml);
        const line = credit.CreditNoteLine[0];
        // when parsing fails to coerce to number, it should keep original string
        expect(typeof line.LineExtensionAmount.value === 'number').toBe(false);
        expect(line.LineExtensionAmount.value).toBe('N/A');
    });
});


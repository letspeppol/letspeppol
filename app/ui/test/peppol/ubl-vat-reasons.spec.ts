import {describe, expect, test} from 'vitest';
import {buildInvoiceXml} from '../../src/services/peppol/ubl-builder';
import {parseInvoice} from '../../src/services/peppol/ubl-parser';
import {NOT_SUBJECT_TO_VAT_REASON_TEXT} from '../../src/services/app/vat-rules';
import type {Invoice} from '../../src/services/peppol/ubl';

describe('UBL VAT exemption reasons', () => {
    test('keeps TaxExemptionReason and TaxExemptionReasonCode on tax subtotal and invoice line', () => {
        const invoice: Invoice = {
            CustomizationID: 'c',
            ProfileID: 'p',
            ID: 'INV-1',
            IssueDate: '2026-06-05',
            InvoiceTypeCode: 380,
            BuyerReference: 'BR',
            DocumentCurrencyCode: 'EUR',
            BillingReference: [],
            AccountingSupplierParty: { Party: { EndpointID: { __schemeID: '0208', value: '123' }, PartyIdentification: [{ ID: { __schemeID: '0208', value: '123' } }], PartyName: { Name: 'Supplier' }, PartyTaxScheme: { CompanyID: { value: 'BE123' }, TaxScheme: { ID: 'VAT' } } } },
            AccountingCustomerParty: { Party: { EndpointID: { __schemeID: '0208', value: '456' }, PartyIdentification: [{ ID: { __schemeID: '0208', value: '456' } }], PartyName: { Name: 'Customer' }, PartyTaxScheme: { CompanyID: { value: 'BE456' }, TaxScheme: { ID: 'VAT' } } } },
            LegalMonetaryTotal: {
                LineExtensionAmount: { __currencyID: 'EUR', value: 100 },
                TaxExclusiveAmount: { __currencyID: 'EUR', value: 100 },
                TaxInclusiveAmount: { __currencyID: 'EUR', value: 100 },
                PayableAmount: { __currencyID: 'EUR', value: 100 }
            },
            TaxTotal: [{
                TaxAmount: { __currencyID: 'EUR', value: 0 },
                TaxSubtotal: [{
                    TaxableAmount: { __currencyID: 'EUR', value: 100 },
                    TaxAmount: { __currencyID: 'EUR', value: 0 },
                    TaxCategory: { ID: 'AE', Percent: 0, TaxExemptionReasonCode: 'VATEX-EU-AE', TaxExemptionReason: 'Reverse charge', TaxScheme: { ID: 'VAT' } }
                }]
            }],
            InvoiceLine: [{
                ID: '1',
                InvoicedQuantity: { __unitCode: 'C62', value: 1 },
                LineExtensionAmount: { __currencyID: 'EUR', value: 100 },
                Item: {
                    Name: 'Line',
                    ClassifiedTaxCategory: { ID: 'AE', Percent: 0, TaxExemptionReasonCode: 'VATEX-EU-AE', TaxExemptionReason: 'Reverse charge', TaxScheme: { ID: 'VAT' } }
                },
                Price: { PriceAmount: { __currencyID: 'EUR', value: 100 } }
            }],
            AdditionalDocumentReference: []
        };

        const xml = buildInvoiceXml(invoice);
        expect(xml).toContain('<cbc:TaxExemptionReasonCode>VATEX-EU-AE</cbc:TaxExemptionReasonCode>');
        expect(xml).toContain('<cbc:TaxExemptionReason>Reverse charge</cbc:TaxExemptionReason>');

        const parsed = parseInvoice(xml);
        expect(parsed.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.TaxExemptionReasonCode).toBe('VATEX-EU-AE');
        expect(parsed.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.TaxExemptionReason).toBe('Reverse charge');
        expect(parsed.InvoiceLine[0].Item.ClassifiedTaxCategory?.TaxExemptionReasonCode).toBe('VATEX-EU-AE');
        expect(parsed.InvoiceLine[0].Item.ClassifiedTaxCategory?.TaxExemptionReason).toBe('Reverse charge');
    });

    test('omits exemption reason fields for zero-rated Z categories', () => {
        const invoice: Invoice = {
            CustomizationID: 'c',
            ProfileID: 'p',
            ID: 'INV-Z',
            IssueDate: '2026-06-05',
            InvoiceTypeCode: 380,
            BuyerReference: 'BR',
            DocumentCurrencyCode: 'EUR',
            BillingReference: [],
            AccountingSupplierParty: { Party: { EndpointID: { __schemeID: '0208', value: '123' }, PartyIdentification: [{ ID: { __schemeID: '0208', value: '123' } }], PartyName: { Name: 'Supplier' }, PartyTaxScheme: { CompanyID: { value: 'BE123' }, TaxScheme: { ID: 'VAT' } } } },
            AccountingCustomerParty: { Party: { EndpointID: { __schemeID: '0208', value: '456' }, PartyIdentification: [{ ID: { __schemeID: '0208', value: '456' } }], PartyName: { Name: 'Customer' }, PartyTaxScheme: { CompanyID: { value: 'BE456' }, TaxScheme: { ID: 'VAT' } } } },
            LegalMonetaryTotal: {
                LineExtensionAmount: { __currencyID: 'EUR', value: 100 },
                TaxExclusiveAmount: { __currencyID: 'EUR', value: 100 },
                TaxInclusiveAmount: { __currencyID: 'EUR', value: 100 },
                PayableAmount: { __currencyID: 'EUR', value: 100 }
            },
            TaxTotal: [{
                TaxAmount: { __currencyID: 'EUR', value: 0 },
                TaxSubtotal: [{
                    TaxableAmount: { __currencyID: 'EUR', value: 100 },
                    TaxAmount: { __currencyID: 'EUR', value: 0 },
                    TaxCategory: { ID: 'Z', Percent: 0, TaxExemptionReason: 'UI only', TaxScheme: { ID: 'VAT' } }
                }]
            }],
            InvoiceLine: [{
                ID: '1',
                InvoicedQuantity: { __unitCode: 'C62', value: 1 },
                LineExtensionAmount: { __currencyID: 'EUR', value: 100 },
                Item: {
                    Name: 'Line',
                    ClassifiedTaxCategory: { ID: 'Z', Percent: 0, TaxExemptionReason: 'UI only', TaxScheme: { ID: 'VAT' } }
                },
                Price: { PriceAmount: { __currencyID: 'EUR', value: 100 } }
            }],
            AdditionalDocumentReference: []
        };

        const xml = buildInvoiceXml(invoice);
        expect(xml).not.toContain('TaxExemptionReason');
        expect(xml).not.toContain('TaxExemptionReasonCode');
    });

    test('omits percent and party tax ids but keeps the mandatory reason for not-subject-to-vat invoices', () => {
        const invoice: Invoice = {
            CustomizationID: 'c',
            ProfileID: 'p',
            ID: 'INV-O',
            IssueDate: '2026-06-05',
            InvoiceTypeCode: 380,
            BuyerReference: 'BR',
            DocumentCurrencyCode: 'EUR',
            BillingReference: [],
            AccountingSupplierParty: { Party: { EndpointID: { __schemeID: '0208', value: '123' }, PartyIdentification: [{ ID: { __schemeID: '0208', value: '123' } }], PartyName: { Name: 'Supplier' } } },
            AccountingCustomerParty: { Party: { EndpointID: { __schemeID: '0208', value: '456' }, PartyIdentification: [{ ID: { __schemeID: '0208', value: '456' } }], PartyName: { Name: 'Customer' } } },
            LegalMonetaryTotal: {
                LineExtensionAmount: { __currencyID: 'EUR', value: 100 },
                TaxExclusiveAmount: { __currencyID: 'EUR', value: 100 },
                TaxInclusiveAmount: { __currencyID: 'EUR', value: 100 },
                PayableAmount: { __currencyID: 'EUR', value: 100 }
            },
            TaxTotal: [{
                TaxAmount: { __currencyID: 'EUR', value: 0 },
                TaxSubtotal: [{
                    TaxableAmount: { __currencyID: 'EUR', value: 100 },
                    TaxAmount: { __currencyID: 'EUR', value: 0 },
                    TaxCategory: { ID: 'O', TaxScheme: { ID: 'VAT' } }
                }]
            }],
            InvoiceLine: [{
                ID: '1',
                InvoicedQuantity: { __unitCode: 'C62', value: 1 },
                LineExtensionAmount: { __currencyID: 'EUR', value: 100 },
                Item: {
                    Name: 'Line',
                    ClassifiedTaxCategory: { ID: 'O', TaxScheme: { ID: 'VAT' } }
                },
                Price: { PriceAmount: { __currencyID: 'EUR', value: 100 } }
            }],
            AdditionalDocumentReference: []
        };

        const xml = buildInvoiceXml(invoice);
        expect(xml).not.toContain('<cbc:Percent>');
        expect(xml).not.toContain('<cac:PartyTaxScheme>');
        expect(xml).toContain(`<cbc:TaxExemptionReason>${NOT_SUBJECT_TO_VAT_REASON_TEXT}</cbc:TaxExemptionReason>`);

        const parsed = parseInvoice(xml);
        expect(parsed.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.TaxExemptionReason).toBe(NOT_SUBJECT_TO_VAT_REASON_TEXT);
        expect(parsed.InvoiceLine[0].Item.ClassifiedTaxCategory?.TaxExemptionReason).toBe(NOT_SUBJECT_TO_VAT_REASON_TEXT);
    });
});

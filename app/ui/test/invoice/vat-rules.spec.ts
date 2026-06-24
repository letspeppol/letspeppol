import {describe, expect, test} from 'vitest';
import {
    applySharedVatReasonText,
    getSharedVatReasonText,
} from '../../src/services/app/vat-rules';
import type {Invoice} from '../../src/services/peppol/ubl';

function createInvoice(): Invoice {
    return {
        CustomizationID: 'c',
        ProfileID: 'p',
        ID: 'INV-SHARED-E',
        IssueDate: '2026-06-24',
        InvoiceTypeCode: 380,
        BuyerReference: 'BR',
        DocumentCurrencyCode: 'EUR',
        BillingReference: [],
        AccountingSupplierParty: { Party: { PartyName: { Name: 'Supplier' } } },
        AccountingCustomerParty: { Party: { PartyName: { Name: 'Customer' } } },
        LegalMonetaryTotal: {
            LineExtensionAmount: { __currencyID: 'EUR', value: 0 },
            TaxExclusiveAmount: { __currencyID: 'EUR', value: 0 },
            TaxInclusiveAmount: { __currencyID: 'EUR', value: 0 },
            PayableAmount: { __currencyID: 'EUR', value: 0 }
        },
        TaxTotal: [],
        InvoiceLine: [
            {
                ID: '1',
                InvoicedQuantity: { __unitCode: 'C62', value: 1 },
                LineExtensionAmount: { __currencyID: 'EUR', value: 10 },
                Item: {
                    Name: 'Existing exempt line',
                    ClassifiedTaxCategory: { ID: 'E', Percent: 0, TaxExemptionReason: 'Article 44 exemption', TaxScheme: { ID: 'VAT' } }
                },
                Price: { PriceAmount: { __currencyID: 'EUR', value: 10 } }
            },
            {
                ID: '2',
                InvoicedQuantity: { __unitCode: 'C62', value: 1 },
                LineExtensionAmount: { __currencyID: 'EUR', value: 15 },
                Item: {
                    Name: 'Second exempt line',
                    ClassifiedTaxCategory: { ID: 'E', Percent: 0, TaxExemptionReason: 'Old text', TaxScheme: { ID: 'VAT' } }
                },
                Price: { PriceAmount: { __currencyID: 'EUR', value: 15 } }
            },
            {
                ID: '3',
                InvoicedQuantity: { __unitCode: 'C62', value: 1 },
                LineExtensionAmount: { __currencyID: 'EUR', value: 20 },
                Item: {
                    Name: 'Reverse charge line',
                    ClassifiedTaxCategory: { ID: 'AE', Percent: 0, TaxExemptionReason: 'Reverse charge', TaxScheme: { ID: 'VAT' } }
                },
                Price: { PriceAmount: { __currencyID: 'EUR', value: 20 } }
            }
        ],
        AdditionalDocumentReference: []
    };
}

describe('shared E VAT reason helpers', () => {
    test('reuses the existing exempt explanation from another line', () => {
        const invoice = createInvoice();

        const reasonText = getSharedVatReasonText(invoice, 'E', invoice.InvoiceLine[1]);

        expect(reasonText).toBe('Article 44 exemption');
    });

    test('applies the saved exempt explanation to every other E line only', () => {
        const invoice = createInvoice();

        applySharedVatReasonText(invoice, 'E', 'Article 44 exemption', invoice.InvoiceLine[0]);

        expect(invoice.InvoiceLine[1].Item.ClassifiedTaxCategory?.TaxExemptionReason).toBe('Article 44 exemption');
        expect(invoice.InvoiceLine[2].Item.ClassifiedTaxCategory?.TaxExemptionReason).toBe('Reverse charge');
    });
});

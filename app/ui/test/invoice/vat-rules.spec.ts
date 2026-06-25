import {describe, expect, test} from 'vitest';
import {
    applySharedVatReasonText,
    getSharedVatReasonText,
    createNotSubjectToVatCategory,
    getDisplayedVatRatePercent,
    getReadonlyDisplayedVatRatePercent,
    NOT_SUBJECT_TO_VAT_REASON_TEXT,
    shouldUseFixedVatMode,
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
    test('creates O categories with the default compliance reason text', () => {
        const category = createNotSubjectToVatCategory();

        expect(category.ID).toBe('O');
        expect(category.Percent).toBeUndefined();
        expect(category.TaxExemptionReason).toBe(NOT_SUBJECT_TO_VAT_REASON_TEXT);
        expect(category.TaxScheme.ID).toBe('VAT');
    });

    test('shows O categories as 0 percent in the VAT dropdown', () => {
        expect(getDisplayedVatRatePercent(createNotSubjectToVatCategory())).toBe(0);
        expect(getDisplayedVatRatePercent({ ID: 'S', Percent: 21, TaxScheme: { ID: 'VAT' } })).toBe(21);
        expect(getDisplayedVatRatePercent(undefined)).toBeUndefined();
    });

    test('shows 0 percent in readonly VAT dropdown when the stored rate is undefined', () => {
        expect(getReadonlyDisplayedVatRatePercent(createNotSubjectToVatCategory())).toBe(0);
        expect(getReadonlyDisplayedVatRatePercent({ ID: 'S', Percent: 12, TaxScheme: { ID: 'VAT' } })).toBe(12);
        expect(getReadonlyDisplayedVatRatePercent(undefined)).toBe(0);
    });

    test('does not force fixed VAT mode when viewing incoming documents', () => {
        expect(shouldUseFixedVatMode(undefined, undefined, true, 'INCOMING')).toBe(false);
        expect(shouldUseFixedVatMode(undefined, undefined, true, 'OUTGOING')).toBe(true);
        expect(shouldUseFixedVatMode('BE0123456789', 'VAT_REGISTERED', true, 'INCOMING')).toBe(false);
    });

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

import {describe, expect, test} from 'vitest';
import {
    applySharedVatReasonText,
    collectVatReasonSelections,
    getSharedVatReasonText,
    createNotSubjectToVatCategory,
    getDisplayedVatRatePercent,
    getReadonlyDisplayedVatRatePercent,
    supportsTaxExemptionReasonText,
    ZERO_VAT_REASON_OPTIONS,
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
        TaxTotal: [{
            TaxAmount: { __currencyID: 'EUR', value: 0 },
            TaxSubtotal: [
                {
                    TaxableAmount: { __currencyID: 'EUR', value: 25 },
                    TaxAmount: { __currencyID: 'EUR', value: 0 },
                    TaxCategory: { ID: 'E', Percent: 0, TaxExemptionReason: 'Article 44 exemption', TaxScheme: { ID: 'VAT' } }
                },
                {
                    TaxableAmount: { __currencyID: 'EUR', value: 45 },
                    TaxAmount: { __currencyID: 'EUR', value: 0 },
                    TaxCategory: { ID: 'AE', Percent: 0, TaxExemptionReason: 'Reverse charge', TaxScheme: { ID: 'VAT' } }
                },
                {
                    TaxableAmount: { __currencyID: 'EUR', value: 30 },
                    TaxAmount: { __currencyID: 'EUR', value: 0 },
                    TaxCategory: { ID: 'Z', Percent: 0, TaxScheme: { ID: 'VAT' } }
                }
            ]
        }],
        InvoiceLine: [
            {
                ID: '1',
                InvoicedQuantity: { __unitCode: 'C62', value: 1 },
                LineExtensionAmount: { __currencyID: 'EUR', value: 10 },
                Item: {
                    Name: 'Existing exempt line',
                    ClassifiedTaxCategory: { ID: 'E', Percent: 0, TaxScheme: { ID: 'VAT' } }
                },
                Price: { PriceAmount: { __currencyID: 'EUR', value: 10 } }
            },
            {
                ID: '2',
                InvoicedQuantity: { __unitCode: 'C62', value: 1 },
                LineExtensionAmount: { __currencyID: 'EUR', value: 15 },
                Item: {
                    Name: 'Second exempt line',
                    ClassifiedTaxCategory: { ID: 'E', Percent: 0, TaxScheme: { ID: 'VAT' } }
                },
                Price: { PriceAmount: { __currencyID: 'EUR', value: 15 } }
            },
            {
                ID: '3',
                InvoicedQuantity: { __unitCode: 'C62', value: 1 },
                LineExtensionAmount: { __currencyID: 'EUR', value: 20 },
                Item: {
                    Name: 'Reverse charge line',
                    ClassifiedTaxCategory: { ID: 'AE', Percent: 0, TaxScheme: { ID: 'VAT' } }
                },
                Price: { PriceAmount: { __currencyID: 'EUR', value: 20 } }
            },
            {
                ID: '4',
                InvoicedQuantity: { __unitCode: 'C62', value: 1 },
                LineExtensionAmount: { __currencyID: 'EUR', value: 25 },
                Item: {
                    Name: 'Second reverse charge line',
                    ClassifiedTaxCategory: { ID: 'AE', Percent: 0, TaxScheme: { ID: 'VAT' } }
                },
                Price: { PriceAmount: { __currencyID: 'EUR', value: 25 } }
            },
            {
                ID: '5',
                InvoicedQuantity: { __unitCode: 'C62', value: 1 },
                LineExtensionAmount: { __currencyID: 'EUR', value: 30 },
                Item: {
                    Name: 'Zero rated line',
                    ClassifiedTaxCategory: { ID: 'Z', Percent: 0, TaxScheme: { ID: 'VAT' } }
                },
                Price: { PriceAmount: { __currencyID: 'EUR', value: 30 } }
            }
        ],
        AdditionalDocumentReference: []
    };
}

describe('shared zero VAT reason helpers', () => {
    test('creates O line categories without storing reason text on the line', () => {
        const category = createNotSubjectToVatCategory();

        expect(category.ID).toBe('O');
        expect(category.Percent).toBeUndefined();
        expect(category.TaxExemptionReason).toBeUndefined();
        expect(category.TaxScheme.ID).toBe('VAT');
    });

    test('stores localized O reason text in the VAT breakdown', () => {
        const invoice = createInvoice();

        applySharedVatReasonText(invoice, 'O', 'Niet onderworpen aan btw');

        const reasonText = getSharedVatReasonText(invoice, 'O');
        expect(reasonText).toBe('Niet onderworpen aan btw');
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

    test('allows exemption reason text only for VAT categories that store it in the VAT breakdown', () => {
        expect(supportsTaxExemptionReasonText('E')).toBe(true);
        expect(supportsTaxExemptionReasonText('K')).toBe(true);
        expect(supportsTaxExemptionReasonText('G')).toBe(true);
        expect(supportsTaxExemptionReasonText('AE')).toBe(true);
        expect(supportsTaxExemptionReasonText('O')).toBe(true);
        expect(supportsTaxExemptionReasonText('Z')).toBe(false);
        expect(supportsTaxExemptionReasonText('S')).toBe(false);
    });

    test('does not expose not-subject-to-vat O as a selectable zero VAT reason', () => {
        expect(ZERO_VAT_REASON_OPTIONS).not.toContain('O');
    });

    test('reuses the existing exempt explanation from another line', () => {
        const invoice = createInvoice();

        const reasonText = getSharedVatReasonText(invoice, 'E', invoice.InvoiceLine[1]);

        expect(reasonText).toBe('Article 44 exemption');
    });

    test('reuses the existing explanation from another line with the same zero VAT category', () => {
        const invoice = createInvoice();

        const reasonText = getSharedVatReasonText(invoice, 'AE', invoice.InvoiceLine[3]);

        expect(reasonText).toBe('Reverse charge');
    });

    test('applies the saved exempt explanation to every other E line only', () => {
        const invoice = createInvoice();

        applySharedVatReasonText(invoice, 'E', 'Article 44 exemption', invoice.InvoiceLine[0]);

        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.TaxExemptionReason).toBe('Article 44 exemption');
        expect(invoice.InvoiceLine[1].Item.ClassifiedTaxCategory?.TaxExemptionReason).toBeUndefined();
        expect(invoice.InvoiceLine[2].Item.ClassifiedTaxCategory?.TaxExemptionReason).toBeUndefined();
    });

    test('applies the saved explanation to every other line with the same zero VAT category only', () => {
        const invoice = createInvoice();

        applySharedVatReasonText(invoice, 'AE', 'Reverse charge', invoice.InvoiceLine[2]);

        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.TaxExemptionReason).toBe('Article 44 exemption');
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[1]?.TaxCategory?.TaxExemptionReason).toBe('Reverse charge');
        expect(invoice.InvoiceLine[0].Item.ClassifiedTaxCategory?.TaxExemptionReason).toBeUndefined();
        expect(invoice.InvoiceLine[3].Item.ClassifiedTaxCategory?.TaxExemptionReason).toBeUndefined();
    });

    test('does not store zero-rated Z reason text in the VAT breakdown', () => {
        const invoice = createInvoice();

        expect(getSharedVatReasonText(invoice, 'Z')).toBeUndefined();
        applySharedVatReasonText(invoice, 'Z', 'Updated zero-rated text', invoice.InvoiceLine[4]);

        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[2]?.TaxCategory?.TaxExemptionReason).toBeUndefined();
        expect(invoice.InvoiceLine[4].Item.ClassifiedTaxCategory?.TaxExemptionReason).toBeUndefined();
        const selections = collectVatReasonSelections(invoice);
        expect(selections.map(item => item.selectedTaxCategoryId)).toEqual(['E', 'AE']);
    });
});

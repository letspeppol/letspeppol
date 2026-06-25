import {describe, expect, test} from 'vitest';
import {InvoiceCalculator} from '../../src/invoice/invoice-calculator';
import {NOT_SUBJECT_TO_VAT_REASON_TEXT} from '../../src/services/app/vat-rules';
import type {ClassifiedTaxCategory, Invoice, InvoiceLine} from '../../src/services/peppol/ubl';

function createInvoice(lines: InvoiceLine[]): Invoice {
    return {
        CustomizationID: 'c',
        ProfileID: 'p',
        ID: 'INV-E',
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
        InvoiceLine: lines,
        AdditionalDocumentReference: []
    };
}

function createLine(id: string, amount: number, taxCategory: ClassifiedTaxCategory): InvoiceLine {
    return {
        ID: id,
        InvoicedQuantity: { __unitCode: 'C62', value: 1 },
        LineExtensionAmount: { __currencyID: 'EUR', value: amount },
        Item: {
            Name: `Line ${id}`,
            ClassifiedTaxCategory: taxCategory,
        },
        Price: { PriceAmount: { __currencyID: 'EUR', value: amount } }
    };
}

describe('InvoiceCalculator', () => {
    test('merges exempt E lines into one VAT breakdown even when explanations differ', () => {
        const invoice = createInvoice([
            createLine('1', 26, { ID: 'E', Percent: 0, TaxExemptionReason: 'EXE', TaxScheme: { ID: 'VAT' } }),
            createLine('2', 14, { ID: 'E', Percent: 0, TaxExemptionReason: 'MOORE', TaxScheme: { ID: 'VAT' } }),
        ]);

        new InvoiceCalculator().calculateTaxAndTotals(invoice);

        expect(invoice.TaxTotal?.[0]?.TaxSubtotal).toHaveLength(1);
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.ID).toBe('E');
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxableAmount.value).toBe(40);
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxAmount.value).toBe(0);
    });

    test('merges AE, G and K VAT breakdown rows by category code and rate', () => {
        const invoice = createInvoice([
            createLine('1', 22, { ID: 'K', Percent: 0, TaxExemptionReasonCode: 'VATEX-EU-IC', TaxExemptionReason: 'INT', TaxScheme: { ID: 'VAT' } }),
            createLine('2', 30, { ID: 'G', Percent: 0, TaxExemptionReasonCode: 'VATEX-EU-G', TaxExemptionReason: 'EXP', TaxScheme: { ID: 'VAT' } }),
            createLine('3', 36, { ID: 'AE', Percent: 0, TaxExemptionReasonCode: 'VATEX-EU-AE', TaxExemptionReason: 'REV', TaxScheme: { ID: 'VAT' } }),
            createLine('4', 42, { ID: 'K', Percent: 0, TaxExemptionReasonCode: 'VATEX-EU-IC', TaxExemptionReason: 'INTRA', TaxScheme: { ID: 'VAT' } }),
            createLine('5', 40, { ID: 'S', Percent: 21, TaxScheme: { ID: 'VAT' } }),
            createLine('6', 36, { ID: 'G', Percent: 0, TaxExemptionReasonCode: 'VATEX-EU-G', TaxExemptionReason: 'EXPO', TaxScheme: { ID: 'VAT' } }),
            createLine('7', 30, { ID: 'AE', Percent: 0, TaxExemptionReasonCode: 'VATEX-EU-AE', TaxExemptionReason: 'REVERZ', TaxScheme: { ID: 'VAT' } }),
            createLine('8', 12, { ID: 'G', Percent: 0, TaxExemptionReasonCode: 'VATEX-EU-G', TaxExemptionReason: 'EXP', TaxScheme: { ID: 'VAT' } }),
        ]);

        new InvoiceCalculator().calculateTaxAndTotals(invoice);

        const subtotals = invoice.TaxTotal?.[0]?.TaxSubtotal ?? [];
        const byId = Object.fromEntries(subtotals.map(item => [item.TaxCategory?.ID ?? '', item]));

        expect(subtotals).toHaveLength(4);
        expect(byId.K?.TaxableAmount.value).toBe(64);
        expect(byId.K?.TaxCategory?.TaxExemptionReasonCode).toBe('VATEX-EU-IC');
        expect(byId.G?.TaxableAmount.value).toBe(78);
        expect(byId.G?.TaxCategory?.TaxExemptionReasonCode).toBe('VATEX-EU-G');
        expect(byId.AE?.TaxableAmount.value).toBe(66);
        expect(byId.AE?.TaxCategory?.TaxExemptionReasonCode).toBe('VATEX-EU-AE');
        expect(byId.S?.TaxableAmount.value).toBe(40);
        expect(byId.S?.TaxAmount.value).toBe(8.4);
        expect(invoice.LegalMonetaryTotal.LineExtensionAmount?.value).toBe(248);
        expect(invoice.LegalMonetaryTotal.TaxInclusiveAmount?.value).toBe(256.4);
    });

    test('keeps the default explanation on not-subject-to-vat breakdown rows', () => {
        const invoice = createInvoice([
            createLine('1', 26, { ID: 'O', TaxScheme: { ID: 'VAT' } }),
            createLine('2', 14, { ID: 'O', TaxExemptionReason: NOT_SUBJECT_TO_VAT_REASON_TEXT, TaxScheme: { ID: 'VAT' } }),
        ]);

        new InvoiceCalculator().calculateTaxAndTotals(invoice);

        expect(invoice.TaxTotal?.[0]?.TaxSubtotal).toHaveLength(1);
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.ID).toBe('O');
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.Percent).toBeUndefined();
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.TaxExemptionReason).toBe(NOT_SUBJECT_TO_VAT_REASON_TEXT);
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxAmount.value).toBe(0);
    });
});

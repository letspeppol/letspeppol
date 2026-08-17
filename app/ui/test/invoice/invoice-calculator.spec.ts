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
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.TaxExemptionReason).toBeUndefined();
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxableAmount.value).toBe(40);
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxAmount.value).toBe(0);
    });

    test('preserves existing VAT breakdown explanations when totals are recalculated', () => {
        const invoice = createInvoice([
            createLine('1', 26, { ID: 'E', Percent: 0, TaxScheme: { ID: 'VAT' } }),
            createLine('2', 14, { ID: 'E', Percent: 0, TaxScheme: { ID: 'VAT' } }),
        ]);
        invoice.TaxTotal = [{
            TaxAmount: { __currencyID: 'EUR', value: 0 },
            TaxSubtotal: [{
                TaxableAmount: { __currencyID: 'EUR', value: 40 },
                TaxAmount: { __currencyID: 'EUR', value: 0 },
                TaxCategory: { ID: 'E', Percent: 0, TaxExemptionReason: 'Header exemption reason', TaxScheme: { ID: 'VAT' } },
            }],
        }];

        new InvoiceCalculator().calculateTaxAndTotals(invoice);

        expect(invoice.TaxTotal?.[0]?.TaxSubtotal).toHaveLength(1);
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.TaxExemptionReason).toBe('Header exemption reason');
        expect(invoice.InvoiceLine[0].Item.ClassifiedTaxCategory?.TaxExemptionReason).toBeUndefined();
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
            createLine('2', 14, { ID: 'O', TaxScheme: { ID: 'VAT' } }),
        ]);

        new InvoiceCalculator().calculateTaxAndTotals(invoice);

        expect(invoice.TaxTotal?.[0]?.TaxSubtotal).toHaveLength(1);
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.ID).toBe('O');
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.Percent).toBeUndefined();
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxCategory?.TaxExemptionReason).toBe(NOT_SUBJECT_TO_VAT_REASON_TEXT);
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxAmount.value).toBe(0);
    });

    test('includes document allowances and charges in VAT and monetary totals', () => {
        const invoice = createInvoice([
            createLine('1', 100, { ID: 'S', Percent: 21, TaxScheme: { ID: 'VAT' } }),
        ]);
        invoice.AllowanceCharge = [
            {
                ChargeIndicator: true,
                Amount: { __currencyID: 'EUR', value: 10 },
                TaxCategory: { ID: 'S', Percent: 21, TaxScheme: { ID: 'VAT' } },
            },
            {
                ChargeIndicator: false,
                Amount: { __currencyID: 'EUR', value: 5 },
                TaxCategory: { ID: 'S', Percent: 21, TaxScheme: { ID: 'VAT' } },
            },
        ];

        new InvoiceCalculator().calculateTaxAndTotals(invoice);

        expect(invoice.TaxTotal?.[0]?.TaxAmount.value).toBe(22.05);
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]?.TaxableAmount.value).toBe(105);
        expect(invoice.LegalMonetaryTotal).toMatchObject({
            LineExtensionAmount: { value: 100 },
            TaxExclusiveAmount: { value: 105 },
            TaxInclusiveAmount: { value: 127.05 },
            AllowanceTotalAmount: { value: 5 },
            ChargeTotalAmount: { value: 10 },
            PayableAmount: { value: 127.05 },
        });
    });
    test('calculates line allowances with header discounts and charges in the VAT breakdown', () => {
        const line = createLine('1', 100, { ID: 'S', Percent: 21, TaxScheme: { ID: 'VAT' } });
        line.AllowanceCharge = [{
            ChargeIndicator: false,
            Amount: { __currencyID: 'EUR', value: 10 },
        }];
        const invoice = createInvoice([line]);
        invoice.AllowanceCharge = [
            {
                ChargeIndicator: false,
                Amount: { __currencyID: 'EUR', value: 5 },
                TaxCategory: { ID: 'S', Percent: 21, TaxScheme: { ID: 'VAT' } },
            },
            {
                ChargeIndicator: true,
                Amount: { __currencyID: 'EUR', value: 8 },
                TaxCategory: { ID: 'S', Percent: 21, TaxScheme: { ID: 'VAT' } },
            },
        ];
        const calculator = new InvoiceCalculator();

        calculator.recalculateLineExtensionAmount(line);
        calculator.calculateTaxAndTotals(invoice);

        expect(line.LineExtensionAmount.value).toBe(90);
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]).toMatchObject({
            TaxableAmount: {value: 93},
            TaxAmount: {value: 19.53},
        });
        expect(invoice.LegalMonetaryTotal).toMatchObject({
            LineExtensionAmount: {value: 90},
            TaxExclusiveAmount: {value: 93},
            TaxInclusiveAmount: {value: 112.53},
            AllowanceTotalAmount: {value: 5},
            ChargeTotalAmount: {value: 8},
            PayableAmount: {value: 112.53},
        });
    });

    test('applies percentage-based line discounts to the full quantity-priced amount', () => {
        const line = createLine('1', 100, { ID: 'S', Percent: 21, TaxScheme: { ID: 'VAT' } });
        line.InvoicedQuantity!.value = 3;
        line.AllowanceCharge = [{
            ChargeIndicator: false,
            MultiplierFactorNumeric: 10,
            Amount: { __currencyID: 'EUR', value: 30 },
        }];
        const invoice = createInvoice([line]);
        const calculator = new InvoiceCalculator();

        calculator.recalculateLineExtensionAmount(line);
        calculator.calculateTaxAndTotals(invoice);

        expect(line.LineExtensionAmount.value).toBe(270);
        expect(invoice.TaxTotal?.[0]?.TaxSubtotal?.[0]).toMatchObject({
            TaxableAmount: {value: 270},
            TaxAmount: {value: 56.7},
        });
        expect(invoice.LegalMonetaryTotal.PayableAmount.value).toBe(326.7);
    });

    test('keeps header allowance charges in their respective VAT-rate breakdowns', () => {
        const standardRateLine = createLine('1', 100, { ID: 'S', Percent: 21, TaxScheme: { ID: 'VAT' } });
        standardRateLine.AllowanceCharge = [{
            ChargeIndicator: false,
            MultiplierFactorNumeric: 10,
            Amount: { __currencyID: 'EUR', value: 10 },
        }];
        const reducedRateLine = createLine('2', 50, { ID: 'S', Percent: 6, TaxScheme: { ID: 'VAT' } });
        const invoice = createInvoice([standardRateLine, reducedRateLine]);
        invoice.AllowanceCharge = [
            {
                ChargeIndicator: false,
                Amount: { __currencyID: 'EUR', value: 5 },
                TaxCategory: { ID: 'S', Percent: 21, TaxScheme: { ID: 'VAT' } },
            },
            {
                ChargeIndicator: true,
                Amount: { __currencyID: 'EUR', value: 2 },
                TaxCategory: { ID: 'S', Percent: 6, TaxScheme: { ID: 'VAT' } },
            },
        ];
        const calculator = new InvoiceCalculator();

        calculator.recalculateLineExtensionAmount(standardRateLine);
        calculator.calculateTaxAndTotals(invoice);

        const subtotals = invoice.TaxTotal?.[0]?.TaxSubtotal ?? [];
        const byRate = Object.fromEntries(subtotals.map(item => [item.TaxCategory?.Percent ?? 0, item]));
        expect(standardRateLine.LineExtensionAmount.value).toBe(90);
        expect(byRate[21]).toMatchObject({
            TaxableAmount: {value: 85},
            TaxAmount: {value: 17.85},
        });
        expect(byRate[6]).toMatchObject({
            TaxableAmount: {value: 52},
            TaxAmount: {value: 3.12},
        });
        expect(invoice.LegalMonetaryTotal).toMatchObject({
            LineExtensionAmount: {value: 140},
            TaxExclusiveAmount: {value: 137},
            TaxInclusiveAmount: {value: 157.97},
            AllowanceTotalAmount: {value: 5},
            ChargeTotalAmount: {value: 2},
            PayableAmount: {value: 157.97},
        });
    });
});

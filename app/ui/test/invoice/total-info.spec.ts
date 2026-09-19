import {describe, expect, test} from 'vitest';
import {getVatBreakdownRows, TotalInfo} from '../../src/invoice/edit/components/tiles/total-info';
import type {Amount, MonetaryTotal, TaxTotal} from '../../src/services/peppol/ubl';

function amount(value: number): Amount {
    return {__currencyID: 'EUR', value};
}

function taxTotal(...subtotals: Array<{percentage?: number; taxAmount: number}>): TaxTotal {
    return {
        TaxAmount: amount(subtotals.reduce((sum, subtotal) => sum + subtotal.taxAmount, 0)),
        TaxSubtotal: subtotals.map(subtotal => ({
            TaxableAmount: amount(100),
            TaxAmount: amount(subtotal.taxAmount),
            TaxCategory: subtotal.percentage === undefined
                ? {ID: 'O', TaxScheme: {ID: 'VAT'}}
                : {ID: 'S', Percent: subtotal.percentage, TaxScheme: {ID: 'VAT'}},
        })),
    };
}

function totalInfo(totals: MonetaryTotal): TotalInfo {
    const result = Object.create(TotalInfo.prototype) as TotalInfo;
    (result as unknown as {
        invoiceContext: {selectedInvoice: {LegalMonetaryTotal: MonetaryTotal}};
    }).invoiceContext = {selectedInvoice: {LegalMonetaryTotal: totals}};
    return result;
}

describe('invoice total info', () => {
    test('does not add a VAT breakdown for a single percentage', () => {
        expect(getVatBreakdownRows(taxTotal({percentage: 21, taxAmount: 21}))).toEqual([]);
    });

    test('groups multiple VAT subtotals by numeric percentage in first-seen order', () => {
        const updatedTaxTotal = taxTotal(
            {percentage: 21, taxAmount: 10},
            {percentage: 6, taxAmount: 3},
            {percentage: 21.0, taxAmount: 5},
            {taxAmount: 0},
        );
        expect(getVatBreakdownRows(updatedTaxTotal)).toEqual([
            {percentage: 21, amount: 15},
            {percentage: 6, amount: 3},
        ]);

        const info = Object.create(TotalInfo.prototype) as TotalInfo;
        info.taxTotalChanged(updatedTaxTotal);
        expect(info.vatBreakdownRows).toEqual([
            {percentage: 21, amount: 15},
            {percentage: 6, amount: 3},
        ]);

        info.taxTotalChanged(taxTotal(
            {percentage: 21, taxAmount: 20},
            {percentage: 6, taxAmount: 4},
        ));
        expect(info.vatBreakdownRows).toEqual([
            {percentage: 21, amount: 20},
            {percentage: 6, amount: 4},
        ]);

        info.taxTotalChanged(taxTotal({percentage: 21, taxAmount: 21}));
        expect(info.vatBreakdownRows).toEqual([]);
    });

    test('initializes the VAT breakdown from the selected invoice when bound', () => {
        const selectedTaxTotal = taxTotal(
            {percentage: 6, taxAmount: 12},
            {percentage: 21, taxAmount: 21},
        );
        const info = Object.create(TotalInfo.prototype) as TotalInfo;
        (info as unknown as {
            invoiceContext: {selectedInvoice: {TaxTotal: TaxTotal[]}};
        }).invoiceContext = {selectedInvoice: {TaxTotal: [selectedTaxTotal]}};

        info.bound();

        expect(info.vatBreakdownRows).toEqual([
            {percentage: 6, amount: 12},
            {percentage: 21, amount: 21},
        ]);
    });

    test('shows positive prepaid and differing payable amounts when tax inclusive is present', () => {
        const info = totalInfo({
            TaxInclusiveAmount: amount(121),
            PrepaidAmount: amount(20),
            PayableAmount: amount(101),
        });

        expect(info.hasTaxInclusiveAmount()).toBe(true);
        expect(info.showPrepaidAmount()).toBe(true);
        expect(info.showPayableAmount()).toBe(true);
    });

    test('hides zero prepaid, equal payable, and totals that cannot be compared', () => {
        const equalTotals = totalInfo({
            TaxInclusiveAmount: amount(121),
            PrepaidAmount: amount(0),
            PayableAmount: amount(121),
        });
        expect(equalTotals.showPrepaidAmount()).toBe(false);
        expect(equalTotals.showPayableAmount()).toBe(false);

        const negativeAmounts = totalInfo({
            TaxInclusiveAmount: amount(121),
            PrepaidAmount: amount(-20),
            PayableAmount: amount(-101),
        });
        expect(negativeAmounts.showPrepaidAmount()).toBe(false);
        expect(negativeAmounts.showPayableAmount()).toBe(false);

        const missingTaxInclusive = totalInfo({PayableAmount: amount(100)});
        expect(missingTaxInclusive.hasTaxInclusiveAmount()).toBe(false);
        expect(missingTaxInclusive.showPayableAmount()).toBe(false);
    });
});

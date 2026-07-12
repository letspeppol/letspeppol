import {describe, expect, test, vi} from 'vitest';
import {InvoiceZeroVatReasonModal} from '../../src/invoice/edit/components/modals/invoice-zero-vat-reason-modal';
import type {InvoiceLine} from '../../src/services/peppol/ubl';

function createLine(): InvoiceLine {
    return {
        ID: '1',
        InvoicedQuantity: { __unitCode: 'C62', value: 1 },
        LineExtensionAmount: { __currencyID: 'EUR', value: 10 },
        Item: {
            Name: 'Line',
            ClassifiedTaxCategory: {
                ID: '',
                Percent: 0,
                TaxScheme: { ID: 'VAT' }
            }
        },
        Price: { PriceAmount: { __currencyID: 'EUR', value: 10 } }
    };
}

describe('InvoiceZeroVatReasonModal', () => {
    test('prefills the exempt explanation when E is selected and one already exists', () => {
        const modal = new InvoiceZeroVatReasonModal();
        const line = createLine();
        modal.getSuggestedVatReasonText = vi.fn().mockReturnValue('Article 44 exemption');

        modal.showModal(line, line.Item.ClassifiedTaxCategory);
        modal.updateReasonId('E');

        expect(modal.reasonId).toBe('E');
        expect(modal.reasonText).toBe('Article 44 exemption');
    });

    test('replaces existing text with a suggested reason when the selected category changes', () => {
        const modal = new InvoiceZeroVatReasonModal();
        const line = createLine();
        modal.getSuggestedVatReasonText = vi.fn().mockReturnValue('Zero-rated suggested text');

        modal.showModal(line, line.Item.ClassifiedTaxCategory);
        modal.reasonText = 'Previous category text';
        modal.updateReasonId('Z');

        expect(modal.reasonId).toBe('Z');
        expect(modal.reasonText).toBe('Zero-rated suggested text');
    });

    test('does not save a reason-required zero VAT category without explanation text', () => {
        const modal = new InvoiceZeroVatReasonModal();
        const line = createLine();

        modal.line = line;
        modal.reasonId = 'AE';
        modal.reasonText = '   ';
        modal.save();

        expect(line.Item.ClassifiedTaxCategory?.ID).toBe('');
    });

    test('saves and records zero-rated Z explanation for analytics', () => {
        const modal = new InvoiceZeroVatReasonModal();
        const line = createLine();
        modal.recordVatReasonSelection = vi.fn();
        modal.syncSharedVatReasonText = vi.fn();

        modal.line = line;
        modal.reasonId = 'Z';
        modal.reasonText = 'Zero-rated goods explanation';
        modal.save();

        expect(line.Item.ClassifiedTaxCategory?.ID).toBe('Z');
        expect(line.Item.ClassifiedTaxCategory?.TaxExemptionReason).toBeUndefined();
        expect(line.Item.ClassifiedTaxCategory?.TaxExemptionReasonCode).toBeUndefined();
        expect(modal.recordVatReasonSelection).toHaveBeenCalledWith('Z', 'Zero-rated goods explanation');
        expect(modal.syncSharedVatReasonText).not.toHaveBeenCalled();
    });
});

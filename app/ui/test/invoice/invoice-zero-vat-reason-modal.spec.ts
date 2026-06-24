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
});

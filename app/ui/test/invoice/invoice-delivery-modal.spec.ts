import { describe, expect, it } from 'vitest';
import { InvoiceDeliveryModal } from '../../src/invoice/edit/components/modals/invoice-delivery-modal';

describe('InvoiceDeliveryModal delivery IDs', () => {
    it.each([
        {id: 'document-1', processedStatus: undefined, expected: 'document-1'},
        {id: 'receipt-1@receiver', processedStatus: undefined, expected: 'receipt-1@receiver'},
        {id: undefined, processedStatus: undefined, expected: '/'},
        {id: '', processedStatus: '', expected: '/'},
        {id: undefined, processedStatus: 'Delivery failed', expected: 'REJECTED'},
        {id: 'document-1', processedStatus: 'Delivery failed', expected: 'document-1'},
    ])('displays $expected for ID $id and status $processedStatus', ({id, processedStatus, expected}) => {
        const modal = new InvoiceDeliveryModal();
        modal.showModal({
            id: 'invoice-1',
            ownerPeppolId: '0208:0685912734',
            partnerPeppolId: '0208:0123456789',
            processedStatus,
        });

        expect(modal.deliveryIdLabel(id)).toBe(expected);
    });
});

import { describe, expect, it } from 'vitest';
import { createFixture } from '@aurelia/testing';
import { I18nConfiguration } from '@aurelia/i18n';
import { InvoiceDeliveryModal } from '../../src/invoice/edit/components/modals/invoice-delivery-modal';
import { DocumentDetailsDto } from '../../src/services/app/invoice-service';
import en from '../../src/app/locale/translation_en.json';

const i18n = I18nConfiguration.customize(options => {
    options.initOptions = {lng: 'en', resources: {en: {translation: en}}};
});

async function mount(details: Partial<DocumentDetailsDto> = {}) {
    const fixture = createFixture(
        '<invoice-delivery-modal component.ref="modal"></invoice-delivery-modal>',
        class { modal: InvoiceDeliveryModal; },
        [InvoiceDeliveryModal, i18n],
    );
    await fixture.started;
    fixture.component.modal.showModal({
        id: 'invoice-1',
        ownerPeppolId: '0208:0685912734',
        partnerPeppolId: '0208:0123456789',
        accessPoint: 'RECOMMAND',
        accessPointId: 'document-1',
        ...details,
    });
    await new Promise(resolve => setTimeout(resolve, 0));
    return fixture;
}

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

describe('InvoiceDeliveryModal delivery stamps', () => {
    it('shows the receipt stamp and timestamp without a receiving AP name', async () => {
        const receivedOn = '2026-10-16T10:00:01Z';
        const fixture = await mount({partnerPeppolMessageId: 'receipt-1@receiver', partnerPeppolMessageOn: receivedOn});

        expect(fixture.getBy('.delivery-stamp-marks')).toBeTruthy();
        expect(fixture.getBy('.delivery-stamp-body').textContent).toBe('Delivered');
        expect(fixture.getBy('.delivery-stamp-date').textContent).toBe(fixture.component.modal.partnerMessageOnLabel);
        expect(fixture.component.modal.partnerMessageOnLabel).not.toBe('/');
    });

    it('shows the receiving AP name when available', async () => {
        const fixture = await mount({accessPoint: 'SCRADA', partnerPeppolAccessPoint: 'PBE000001'});

        expect(fixture.getBy('.delivery-stamp-body').textContent).toBe('PBE000001');
    });

    it('does not show a success stamp without receipt metadata', async () => {
        const fixture = await mount();

        expect(fixture.appHost.querySelector('.delivery-stamp-marks')).toBeNull();
    });

    it('shows the error stamp when delivery failed', async () => {
        const fixture = await mount({processedStatus: 'Delivery failed', processedOn: '2026-10-16T10:00:01Z'});

        expect(fixture.getBy('.delivery-stamp-mark.error .delivery-stamp-body').textContent).toBe('Delivery failed');
        expect(fixture.appHost.querySelector('.delivery-stamp-mark:not(.error)')).toBeNull();
    });
});

import { describe, expect, it } from 'vitest';
import { InvoicePaymentQrModal } from '../../src/invoice/edit/components/modals/invoice-payment-qr-modal';

describe('InvoicePaymentQrModal SEPA QR reference fields', () => {
    it('uses valid Belgian PaymentID values as structured creditor references', () => {
        const modal = new InvoicePaymentQrModal();
        modal.invoiceContext = {
            selectedInvoice: {
                ID: 'INV-2026-0001',
                PaymentMeans: {
                    PaymentID: '+++123/4567/89002+++'
                }
            }
        };

        expect(modal.reference).toBe('+++123/4567/89002+++');
        expect(modal.creditorReference).toBe('123456789002');
        expect(modal.remittanceInformation).toBe('');
    });

    it('keeps invalid or ordinary references as unstructured remittance information', () => {
        const modal = new InvoicePaymentQrModal();
        modal.invoiceContext = {
            selectedInvoice: {
                ID: 'INV-2026-0001',
                PaymentMeans: {
                    PaymentID: '+++123/4567/89012+++'
                }
            }
        };

        expect(modal.creditorReference).toBe('');
        expect(modal.remittanceInformation).toBe('+++123/4567/89012+++');
    });

    it('does not promote invoice number fallbacks to structured references', () => {
        const modal = new InvoicePaymentQrModal();
        modal.invoiceContext = {
            selectedInvoice: {
                ID: '123456789002',
                PaymentMeans: {}
            }
        };

        expect(modal.reference).toBe('123456789002');
        expect(modal.creditorReference).toBe('');
        expect(modal.remittanceInformation).toBe('123456789002');
    });
});

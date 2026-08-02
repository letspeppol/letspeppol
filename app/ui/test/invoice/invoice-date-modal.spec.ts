import { describe, expect, it, vi } from 'vitest';
import { DocumentType } from '../../src/services/app/invoice-service';

const invoiceComposer = vi.hoisted(() => ({
    translatePaymentTerm: vi.fn((paymentTerm: string) => {
        const translations: Record<string, string> = {
            '15_DAYS': '15 days',
            '30_DAYS': '30 days',
            '60_DAYS': '60 days',
            END_OF_NEXT_MONTH: 'End of next month',
        };
        return translations[paymentTerm] ?? paymentTerm;
    }),
    getDueDate: vi.fn((paymentTerm: string, issueDate: string) => {
        if (issueDate !== '2026-07-05') {
            return issueDate;
        }
        const dueDates: Record<string, string> = {
            '15_DAYS': '2026-07-20',
            '30_DAYS': '2026-08-04',
            '60_DAYS': '2026-09-03',
            END_OF_NEXT_MONTH: '2026-08-31',
        };
        return dueDates[paymentTerm] ?? issueDate;
    }),
}));

vi.mock('@aurelia/kernel', async importOriginal => ({
    ...await importOriginal<typeof import('@aurelia/kernel')>(),
    resolve: () => invoiceComposer,
}));

import { InvoiceDateModal } from '../../src/invoice/edit/components/modals/invoice-date-modal';

describe('InvoiceDateModal payment terms', () => {
    it('opens invoices without payment terms and preserves their due date', () => {
        const modal = new InvoiceDateModal();
        modal.documentType = DocumentType.INVOICE;
        modal.invoiceContext = {
            selectedInvoice: {
                IssueDate: '2026-07-05',
                DueDate: '2026-08-04',
                PaymentTerms: undefined,
            },
        };

        expect(() => modal.showModal()).not.toThrow();
        expect(modal.open).toBe(true);
        expect(modal.dueDate).toBe('2026-08-04');
        expect(modal.selectedPaymentTerm).toBe('30_DAYS');
    });

    it('leaves unmatched due dates unchanged when no payment term is set', () => {
        const modal = new InvoiceDateModal();
        modal.documentType = DocumentType.INVOICE;
        modal.invoiceContext = {
            selectedInvoice: {
                IssueDate: '2026-07-05',
                DueDate: '2026-07-31',
                PaymentTerms: undefined,
            },
        };

        modal.showModal();

        expect(modal.dueDate).toBe('2026-07-31');
        expect(modal.selectedPaymentTerm).toBeUndefined();
    });
});

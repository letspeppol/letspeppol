import { describe, expect, it, vi } from 'vitest';
import { DocumentType } from '../../src/services/app/invoice-service';
import {
    calculateDueDate,
    formatPaymentTermCode,
    parsePaymentTermCode,
    preferredTerm,
} from '../../src/services/app/payment-terms';

vi.mock('@aurelia/kernel', async importOriginal => ({
    ...await importOriginal<typeof import('@aurelia/kernel')>(),
    resolve: () => invoiceComposer,
}));

const invoiceComposer = {
    translatePaymentTerm: (code: string) => {
        const term = parsePaymentTermCode(code);
        const days = `${term.days} ${term.days === 1 ? 'day' : 'days'}`;
        if (term.basis === 'END_OF_MONTH') {
            return term.days === 0 ? 'End of month' : `${days} end of month`;
        }
        if (term.basis === 'END_OF_NEXT_MONTH') {
            return term.days === 0 ? 'End of next month' : `${days} end of next month`;
        }
        return term.days === 0 ? 'Due on receipt' : days;
    },
    getDueDate: (code: string, issueDate: string) =>
        calculateDueDate(parsePaymentTermCode(code), issueDate) ?? issueDate,
    derivePaymentTermCode: (issueDate: string, dueDate?: string, note?: string) => {
        const fromNote = KNOWN_CODES.find(code => invoiceComposer.translatePaymentTerm(code) === note?.trim());
        if (fromNote && (!dueDate || invoiceComposer.getDueDate(fromNote, issueDate) === dueDate)) {
            return fromNote;
        }
        const term = dueDate ? preferredTerm(issueDate, dueDate) : undefined;
        return term ? formatPaymentTermCode(term) : undefined;
    },
};

const KNOWN_CODES = ['NET_0', 'NET_15', 'NET_30', 'NET_45', 'NET_60', 'EOM_0', 'EOM_30', 'EONM_0'];

import { InvoiceDateModal } from '../../src/invoice/edit/components/modals/invoice-date-modal';

type ModalInternals = { saveDate(): void };

function save(modal: InvoiceDateModal) {
    (modal as unknown as ModalInternals).saveDate();
}

function openModal(invoice: Record<string, unknown>, documentType = DocumentType.INVOICE) {
    const modal = new InvoiceDateModal();
    modal.documentType = documentType;
    modal.invoiceContext = {selectedInvoice: invoice};
    modal.showModal();
    return modal;
}

function edit(modal: InvoiceDateModal, field: 'issueDate' | 'dueDate' | 'selectedPaymentTerm', value: unknown) {
    modal[field] = value;
    modal[`${field}Changed`]();
}

describe('InvoiceDateModal payment terms', () => {
    it('opens invoices without payment terms and preserves their due date', () => {
        const modal = openModal({IssueDate: '2026-07-05', DueDate: '2026-08-04', PaymentTerms: undefined});

        expect(modal.open).toBe(true);
        expect(modal.dueDate).toBe('2026-08-04');
        expect(modal.selectedPaymentTerm).toBe('NET_30');
    });

    it('states a due date that matches no preset as its own day count', () => {
        const modal = openModal({IssueDate: '2026-07-05', DueDate: '2026-07-31', PaymentTerms: undefined});

        expect(modal.dueDate).toBe('2026-07-31');
        expect(modal.selectedPaymentTerm).toBe('EOM_0');
    });

    it('fills in a missing due date from the term in the note', () => {
        const modal = openModal({IssueDate: '2026-07-05', DueDate: undefined, PaymentTerms: {Note: '30 days'}});

        expect(modal.selectedPaymentTerm).toBe('NET_30');
        expect(modal.dueDate).toBe('2026-08-04');
    });

    it('leaves the due date empty when there is no term to compute it from', () => {
        const modal = openModal({IssueDate: '2026-07-05', DueDate: undefined, PaymentTerms: undefined});

        expect(modal.selectedPaymentTerm).toBeUndefined();
        expect(modal.dueDate).toBeUndefined();
    });

    it('recalculates the due date when the term changes', () => {
        const modal = openModal({IssueDate: '2026-07-05', DueDate: '2026-08-04', PaymentTerms: undefined});

        edit(modal, 'selectedPaymentTerm', 'EOM_30');

        expect(modal.dueDate).toBe('2026-08-30');
    });

    it('recalculates the due date when the issue date changes', () => {
        const modal = openModal({IssueDate: '2026-07-05', DueDate: '2026-08-04', PaymentTerms: undefined});

        edit(modal, 'issueDate', '2026-07-10');

        expect(modal.dueDate).toBe('2026-08-09');
    });

    it('restates a hand-typed due date as a day count', () => {
        const modal = openModal({IssueDate: '2026-07-05', DueDate: '2026-08-04', PaymentTerms: undefined});

        edit(modal, 'dueDate', '2026-07-20');

        expect(modal.selectedPaymentTerm).toBe('NET_15');
        expect(modal.dueDate).toBe('2026-07-20');
    });

    it('leaves the due date alone on credit notes', () => {
        const modal = openModal({IssueDate: '2026-07-05', PaymentTerms: {Note: '15 days'}}, DocumentType.CREDIT_NOTE);

        edit(modal, 'selectedPaymentTerm', 'NET_30');

        expect(modal.dueDate).toBeUndefined();
    });

    it('writes the note for the selected term on save', () => {
        const invoice: Record<string, unknown> = {IssueDate: '2026-07-05', DueDate: '2026-08-04', PaymentTerms: undefined};
        const modal = openModal(invoice);

        edit(modal, 'selectedPaymentTerm', 'EOM_0');
        save(modal);

        expect(invoice.DueDate).toBe('2026-07-31');
        expect(invoice.PaymentTerms).toEqual({Note: 'End of month'});
    });

    it('drops the note when the user clears a term it recognised', () => {
        const invoice: Record<string, unknown> = {IssueDate: '2026-07-05', DueDate: '2026-08-04', PaymentTerms: {Note: '30 days'}};
        const modal = openModal(invoice);

        edit(modal, 'selectedPaymentTerm', undefined);
        save(modal);

        expect(invoice.PaymentTerms).toBeUndefined();
    });

    it('keeps a note it could not read when no term was chosen', () => {
        const invoice: Record<string, unknown> = {IssueDate: '2026-07-05', PaymentTerms: {Note: 'Payable per contract'}};
        const modal = openModal(invoice, DocumentType.CREDIT_NOTE);

        save(modal);

        expect(invoice.PaymentTerms).toEqual({Note: 'Payable per contract'});
    });
});

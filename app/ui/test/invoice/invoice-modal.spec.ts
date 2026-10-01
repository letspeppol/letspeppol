import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DocumentType } from '../../src/services/app/invoice-service';

const services = vi.hoisted(() => ({
    getDocuments: vi.fn(),
    tr: vi.fn((key: string) => key),
}));

vi.mock('@aurelia/kernel', async importOriginal => ({
    ...await importOriginal<typeof import('@aurelia/kernel')>(),
    resolve: () => services,
}));

import { InvoiceModal } from '../../src/invoice/edit/components/modals/invoice-modal';

function documentPage(references: string[]) {
    return {
        content: references.map(reference => ({
            invoiceReference: reference,
            issueDate: '2026-07-05T00:00:00',
            processedOn: '2026-07-06T00:00:00',
            processedStatus: undefined,
        })),
    };
}

function invoiceContext(billingReferenceId?: string) {
    return {
        selectedInvoice: {
            AccountingCustomerParty: {Party: {EndpointID: {__schemeID: '0208', value: '0123456789'}}},
            BillingReference: billingReferenceId
                ? [{InvoiceDocumentReference: {ID: billingReferenceId, IssueDate: '2026-07-05'}}]
                : undefined,
        },
    };
}

function openedCreditNoteModal() {
    const modal = new InvoiceModal();
    modal.invoiceContext = invoiceContext();
    modal.originalDocumentType = DocumentType.CREDIT_NOTE;
    return modal;
}

describe('InvoiceModal referenceable invoices', () => {
    beforeEach(() => services.getDocuments.mockReset());

    it('loads the list when the document type is switched to credit note', async () => {
        services.getDocuments.mockResolvedValue(documentPage(['INV-2026-0001']));
        const modal = new InvoiceModal();
        modal.invoiceContext = invoiceContext();
        modal.originalDocumentType = DocumentType.INVOICE;
        modal.showModal();
        await vi.waitFor(() => expect(modal.referenceableInvoices).toEqual([]));

        modal.selectedDocumentType = DocumentType.CREDIT_NOTE;

        await vi.waitFor(() => expect(modal.referenceableInvoices).toHaveLength(1));
        expect(modal.referenceableInvoices[0].invoiceReference).toBe('INV-2026-0001');
    });

    it('drops the previous document list when the modal is reopened', async () => {
        services.getDocuments.mockResolvedValue(documentPage(['INV-2026-0001']));
        const modal = openedCreditNoteModal();
        modal.showModal();
        await vi.waitFor(() => expect(modal.referenceableInvoices).toHaveLength(1));

        let resolveSecond: (page: unknown) => void;
        services.getDocuments.mockReturnValue(new Promise(resolve => { resolveSecond = resolve; }));
        modal.showModal();

        expect(modal.referenceableInvoices).toEqual([]);
        resolveSecond(documentPage(['INV-2026-0002']));
        await vi.waitFor(() => expect(modal.referenceableInvoices).toHaveLength(1));
        expect(modal.referenceableInvoices[0].invoiceReference).toBe('INV-2026-0002');
    });

    it('ignores a lookup that was started for a previously opened document', async () => {
        let resolveStale: (page: unknown) => void;
        services.getDocuments.mockReturnValueOnce(new Promise(resolve => { resolveStale = resolve; }));
        const modal = new InvoiceModal();
        modal.originalDocumentType = DocumentType.CREDIT_NOTE;
        modal.invoiceContext = invoiceContext('INV-STALE');
        modal.showModal();

        services.getDocuments.mockResolvedValue(documentPage(['INV-2026-0002']));
        modal.invoiceContext = invoiceContext('INV-2026-0002');
        modal.showModal();
        await vi.waitFor(() => expect(modal.selectedInvoiceReferenceId).toBe('INV-2026-0002'));

        resolveStale(documentPage(['INV-STALE']));
        await Promise.resolve();
        await Promise.resolve();

        expect(modal.selectedInvoiceReferenceId).toBe('INV-2026-0002');
        expect(modal.referenceableInvoices.map(inv => inv.invoiceReference)).toEqual(['INV-2026-0002']);
    });

    it('looks the list up once per open', async () => {
        services.getDocuments.mockResolvedValue(documentPage(['INV-2026-0001']));
        const modal = openedCreditNoteModal();

        modal.showModal();
        await vi.waitFor(() => expect(modal.referenceableInvoices).toHaveLength(1));

        expect(services.getDocuments).toHaveBeenCalledTimes(1);
    });
});

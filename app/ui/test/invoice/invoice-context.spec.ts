import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DocumentDirection, DocumentDto, DocumentType } from '../../src/services/app/invoice-service';
import { sampleCreditNoteXml, sampleInvoiceXml } from '../peppol/ubl-test-utils';

const services = vi.hoisted(() => ({
    getAndSetMyCompanyForToken: vi.fn(),
    calculateTaxAndTotals: vi.fn(),
    createInvoice: vi.fn(() => ({InvoiceTypeCode: 380, InvoiceLine: []})),
    createCreditNote: vi.fn(() => ({CreditNoteTypeCode: 381, CreditNoteLine: []})),
    searchPartners: vi.fn(() => Promise.resolve([])),
    publish: vi.fn(),
    tr: vi.fn((key: string) => key),
}));

vi.mock('@aurelia/kernel', async importOriginal => ({
    ...await importOriginal<typeof import('@aurelia/kernel')>(),
    resolve: () => services,
}));

import { InvoiceContext } from '../../src/invoice/invoice-context';
import { PaymentInfo } from '../../src/invoice/edit/components/tiles/payment-info';

const company = (invoiceRef: string, creditNoteRef: string) => ({
    lastInvoiceReference: invoiceRef,
    lastCreditNoteReference: creditNoteRef,
});

describe('InvoiceContext', () => {
    it('only offers a payment QR for an ordinary received invoice', () => {
        const context = new InvoiceContext();
        const paymentInfo = new PaymentInfo();
        Reflect.set(paymentInfo, 'invoiceContext', context);
        const document = {
            type: DocumentType.INVOICE,
            direction: DocumentDirection.INCOMING,
            processedOn: '2026-10-18T12:00:00Z',
            ubl: sampleInvoiceXml,
        } as DocumentDto;
        context.selectInvoice(document);
        expect(paymentInfo.shouldShowPaymentQrAction).toBe(true);
        context.selectInvoice({...document, type: DocumentType.SELF_BILLING_INVOICE, ubl: document.ubl.replace('>380<', '>389<')});
        expect(paymentInfo.shouldShowPaymentQrAction).toBe(false);
    });

    it.each([
        [DocumentType.SELF_BILLING_INVOICE, sampleInvoiceXml.replaceAll('poacc:billing:', 'poacc:selfbilling:').replace('>380<', '>389<')],
        [DocumentType.SELF_BILLING_CREDIT_NOTE, sampleCreditNoteXml.replaceAll('poacc:billing:', 'poacc:selfbilling:').replace('>381<', '>261<')],
    ])('opens a received self-billed %s with its customer as partner', (type, ubl) => {
        const context = new InvoiceContext();
        context.selectInvoice({type, ubl, direction: DocumentDirection.INCOMING} as DocumentDto);

        expect(context.readOnly).toBe(true);
        expect(context.selfBilling).toBe(true);
        expect(context.selectedDocumentType).toBe(type);
        expect(context.partnerParty).toBe(context.selectedInvoice.AccountingCustomerParty.Party);
        expect(context.lines.length).toBeGreaterThan(0);
    });

    it('keeps an outgoing self-billed document read only and uses the supplier as partner', () => {
        const context = new InvoiceContext();
        context.selectInvoice({
            type: DocumentType.SELF_BILLING_INVOICE,
            ubl: sampleInvoiceXml.replace('>380<', '>389<'),
            direction: DocumentDirection.OUTGOING,
        } as DocumentDto);
        expect(context.readOnly).toBe(true);
        expect(context.partnerParty).toBe(context.selectedInvoice.AccountingSupplierParty.Party);
    });

    beforeEach(() => {
        services.getAndSetMyCompanyForToken.mockReset();
        services.calculateTaxAndTotals.mockReset();
    });

    it('derives the lines from the selected invoice', () => {
        const context = new InvoiceContext();

        context.selectedInvoice = {InvoiceTypeCode: 380, InvoiceLine: [{ID: '1'}]} as never;

        expect(context.lines).toEqual([{ID: '1'}]);
    });

    it('reads the reference sequence of the document type it was called for', async () => {
        services.getAndSetMyCompanyForToken.mockResolvedValue(company('INV-20260001', 'CRN-20260007'));
        const context = new InvoiceContext();
        context.selectedDocumentType = DocumentType.CREDIT_NOTE;

        await context.getLastInvoiceReference();

        expect(context.nextReference).toBe('CRN-20260008');
    });

    it('ignores an answer for a document type that is no longer open', async () => {
        let resolveStale: (value: unknown) => void;
        services.getAndSetMyCompanyForToken.mockReturnValueOnce(new Promise(resolve => { resolveStale = resolve; }));
        const context = new InvoiceContext();

        context.selectedDocumentType = DocumentType.CREDIT_NOTE;
        const stale = context.getLastInvoiceReference();

        services.getAndSetMyCompanyForToken.mockResolvedValue(company('INV-20260001', 'CRN-20260007'));
        context.selectedDocumentType = DocumentType.INVOICE;
        await context.getLastInvoiceReference();
        expect(context.nextReference).toBe('INV-20260002');

        resolveStale(company('INV-20260001', 'CRN-20260007'));
        await stale;

        expect(context.nextReference).toBe('INV-20260002');
    });

    it('clears the suggestion while a new one is being looked up', () => {
        services.getAndSetMyCompanyForToken.mockReturnValue(new Promise(() => {                     }));
        const context = new InvoiceContext();
        context.nextReference = 'CRN-20260008';

        context.getLastInvoiceReference();

        expect(context.nextReference).toBeUndefined();
    });

    it('records the document type a new document was created for', () => {
        const context = new InvoiceContext();

        context.newUBLDocument(DocumentType.CREDIT_NOTE);

        expect(context.selectedDocumentType).toBe(DocumentType.CREDIT_NOTE);
    });

    it('drops every piece of account scoped state when the account is switched', () => {
        services.getAndSetMyCompanyForToken.mockReturnValue(new Promise(() => {                     }));
        const context = new InvoiceContext();
        context.selectedInvoice = {InvoiceTypeCode: 380, InvoiceLine: [{ID: '1'}]} as never;
        context.selectedDocument = {id: 7} as never;
        context.selectedRouteId = '7';
        context.selectedDocumentType = DocumentType.CREDIT_NOTE;
        context.activeBox = 'DRAFTS';
        context.lastReference = 'CRN-20260007';
        context.nextReference = 'CRN-20260008';
        context.readOnly = true;
        context.partnerMissing = true;
        context.addPdfToSendingInvoice = true;
        context.draftPage.content.push({id: 7} as never);
        context.invoicePage.content.push({id: 8} as never);

        context.clearAccountCache();

        expect(context.selectedInvoice).toBeUndefined();
        expect(context.selectedDocument).toBeUndefined();
        expect(context.selectedRouteId).toBeUndefined();
        expect(context.selectedDocumentType).toBe(DocumentType.INVOICE);
        expect(context.activeBox).toBe('ALL');
        expect(context.lines).toBeUndefined();
        expect(context.lastReference).toBeUndefined();
        expect(context.nextReference).toBeUndefined();
        expect(context.readOnly).toBe(false);
        expect(context.partnerMissing).toBe(false);
        expect(context.addPdfToSendingInvoice).toBe(false);
        expect(context.draftPage.content).toEqual([]);
        expect(context.invoicePage.content).toEqual([]);
    });

    it('does not let a reference lookup from the previous account land', async () => {
        let resolveStale: (value: unknown) => void;
        services.getAndSetMyCompanyForToken.mockReturnValueOnce(new Promise(resolve => { resolveStale = resolve; }));
        const context = new InvoiceContext();
        const stale = context.getLastInvoiceReference();

        context.clearAccountCache();
        resolveStale(company('INV-20260001', 'CRN-20260007'));
        await stale;

        expect(context.nextReference).toBeUndefined();
    });
});

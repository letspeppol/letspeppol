import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DocumentDirection, DocumentDto, DocumentType } from '../../src/services/app/invoice-service';
import { sampleCreditNoteXml, sampleInvoiceXml } from '../peppol/ubl-test-utils';

const services = vi.hoisted(() => ({
    validate: vi.fn(),
    createDocument: vi.fn(),
    sendDocument: vi.fn(),
    searchPartners: vi.fn(() => Promise.resolve([])),
    recordVatReasonSelections: vi.fn(() => Promise.resolve()),
    publish: vi.fn(),
    tr: vi.fn((key: string) => key),
}));

vi.mock('@aurelia/kernel', async importOriginal => ({
    ...await importOriginal<typeof import('@aurelia/kernel')>(),
    resolve: () => services,
}));

import { InvoiceContext } from '../../src/invoice/invoice-context';
import { InvoiceEdit } from '../../src/invoice/edit/invoice-edit';
import { UploadUblModal } from '../../src/invoice/overview/components/upload-ubl-modal';

describe('self-billing uploads', () => {
    beforeEach(() => vi.clearAllMocks());

    it.each([
        [DocumentType.SELF_BILLING_INVOICE, sampleInvoiceXml.replaceAll('poacc:billing:', 'poacc:selfbilling:').replace('>380<', '>389<')],
        [DocumentType.SELF_BILLING_CREDIT_NOTE, sampleCreditNoteXml.replaceAll('poacc:billing:', 'poacc:selfbilling:').replace('>381<', '>261<')],
    ])('uploads and sends %s using the stored XML while keeping its editor read only', async (type, xml) => {
        const context = new InvoiceContext();
        const uploaded = {id: 'uploaded-1', type, ubl: xml, direction: DocumentDirection.OUTGOING,
            draftedOn: '2026-10-18T12:00:00Z', createdExternally: true} as DocumentDto;
        services.validate.mockResolvedValue({isValid: true});
        services.createDocument.mockResolvedValue(uploaded);
        services.sendDocument.mockResolvedValue({...uploaded, draftedOn: undefined, proxyOn: '2026-10-18T13:00:00Z'});
        const upload = new UploadUblModal();
        Reflect.set(upload, 'invoiceContext', context);
        vi.spyOn(upload, 'readXmlAsString').mockResolvedValue(xml);
        upload.showModal();
        await upload.dragDrop({preventDefault: vi.fn(), dataTransfer: {
            files: [new File([xml], 'self-billed.xml', {type: 'application/xml'})],
        }} as unknown as DragEvent);

        expect(services.createDocument).toHaveBeenCalledWith(xml, true, true);
        expect(upload.open).toBe(false);
        expect(context.selectedDocumentType).toBe(type);
        expect(context.readOnly).toBe(true);
        expect(context.selectedDocument.ubl).toBe(xml);

        const editor = new InvoiceEdit();
        Reflect.set(editor, 'invoiceContext', context);
        editor.selectedDocumentType = type;
        editor.readOnly = true;
        vi.spyOn(editor, 'returnToOverview').mockImplementation(() => {});
        const build = vi.spyOn(editor, 'buildXml');
        // Editor limits on negative totals must not block sending validated uploaded XML.
        context.selectedInvoice.LegalMonetaryTotal.LineExtensionAmount.value = -100;
        expect(editor.isValid).toBe(true);
        await editor.sendInvoice();
        expect(services.sendDocument).toHaveBeenCalledWith(uploaded.id);
        expect(build).not.toHaveBeenCalled();
        expect(context.selectedDocument.ubl).toBe(xml);
    });

    it.each([DocumentType.SELF_BILLING_INVOICE, DocumentType.SELF_BILLING_CREDIT_NOTE])(
        'does not create %s in the UI editor', type => {
            expect(() => new InvoiceContext().newUBLDocument(type)).toThrow();
        });
});

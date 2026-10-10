import { describe, expect, test } from 'vitest';
import moment from 'moment';
import { GENERATED_INVOICE, InvoiceComposer } from '../../src/invoice/invoice-composer';
import { DocumentType } from '../../src/services/app/invoice-service';
import { CreditNote, Invoice } from '../../src/services/peppol/ubl';

describe('InvoiceComposer', () => {
    test('adds the company enterprise number to the supplier legal entity', () => {
        const composer = Object.create(InvoiceComposer.prototype) as InvoiceComposer;
        (composer as any).companyService = {
            myCompany: {
                peppolId: '0208:1234567890',
                identifier: '0123456789',
                vatNumber: 'BE0123456789',
                name: 'Supplier Legal Name',
                displayName: 'Supplier Display Name',
                registeredOffice: {
                    street: 'Main Street',
                    houseNumber: '1',
                    city: 'Brussels',
                    postalCode: '1000',
                },
            },
        };

        const supplier = composer.getAccountingSupplierParty().Party;

        expect(supplier.PartyTaxScheme?.CompanyID?.value).toBe('BE0123456789');
        expect(supplier.PartyLegalEntity?.CompanyID?.value).toBe('0123456789');
    });

    test('duplicates an invoice without carrying over the document number', () => {
        const composer = composerWithCompany();
        const source = {
            ID: 'INV-20250012',
            IssueDate: '2025-01-10',
            DueDate: '2025-02-09',
            InvoiceTypeCode: 380,
            BuyerReference: 'PO-42',
            PaymentMeans: {
                PaymentID: '+++090/9337/55493+++',
                PayeeFinancialAccount: { ID: 'BE68539007547034' },
            },
            AdditionalDocumentReference: [
                { ID: GENERATED_INVOICE, Attachment: { EmbeddedDocumentBinaryObject: { value: 'JVBERi0xLjc=' } } },
                { ID: 'timesheet.csv', Attachment: { EmbeddedDocumentBinaryObject: { value: 'YSxiLGM=' } } },
            ],
            InvoiceLine: [{ ID: '1', LineExtensionAmount: { value: 100 } }],
        } as unknown as Invoice;

        const copy = composer.duplicate(source, DocumentType.INVOICE) as Invoice;

        expect(copy.ID).toBe('');
        expect(copy.IssueDate).toBe(moment().format('YYYY-MM-DD'));
        expect(copy.DueDate).toBe(moment().add(30, 'day').format('YYYY-MM-DD'));
        expect(copy.PaymentMeans.PaymentID).toBeUndefined();
        expect(copy.PaymentMeans.PayeeFinancialAccount.ID).toBe('BE68539007547034');
        expect(copy.BuyerReference).toBe('PO-42');
        expect(copy.InvoiceLine).toEqual(source.InvoiceLine);
        expect(copy.AdditionalDocumentReference[0].Attachment.EmbeddedDocumentBinaryObject.value).toBe('ZW1wdHk=');
        expect(copy.AdditionalDocumentReference[1].Attachment.EmbeddedDocumentBinaryObject.value).toBe('YSxiLGM=');
        expect(source.ID).toBe('INV-20250012');
        expect(source.PaymentMeans.PaymentID).toBe('+++090/9337/55493+++');
    });

    test('falls back to the company payment terms when the source has no due date', () => {
        const composer = composerWithCompany('15_DAYS');
        const source = { ID: 'INV-1', IssueDate: '2025-01-10' } as unknown as Invoice;

        const copy = composer.duplicate(source, DocumentType.INVOICE);

        expect(copy.DueDate).toBe(moment().add(15, 'day').format('YYYY-MM-DD'));
    });

    test('duplicates a credit note without adding a due date', () => {
        const composer = composerWithCompany();
        const source = {
            ID: 'CRN-20250003',
            IssueDate: '2025-01-10',
            CreditNoteTypeCode: 381,
            PaymentTerms: { Note: '15 days' },
            CreditNoteLine: [{ ID: '1', LineExtensionAmount: { value: 50 } }],
        } as unknown as CreditNote;

        const copy = composer.duplicate(source, DocumentType.CREDIT_NOTE) as CreditNote;

        expect(copy.ID).toBe('');
        expect(copy.IssueDate).toBe(moment().format('YYYY-MM-DD'));
        expect(copy.DueDate).toBeUndefined();
        expect(copy.PaymentTerms).toEqual({ Note: '15 days' });
        expect(copy.CreditNoteLine).toEqual(source.CreditNoteLine);
    });
});

function composerWithCompany(paymentTerms?: string): InvoiceComposer {
    const composer = Object.create(InvoiceComposer.prototype) as InvoiceComposer;
    Object.assign(composer, { companyService: { myCompany: { paymentTerms } } });
    return composer;
}

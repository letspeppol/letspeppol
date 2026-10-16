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

    test('recalculates the due date from the payment terms of the source', () => {
        const composer = composerWithCompany('30_DAYS');
        const source = {
            ID: 'INV_2026_0013',
            IssueDate: '2026-08-02',
            PaymentTerms: { Note: '15 days' },
        } as unknown as Invoice;

        const copy = composer.duplicate(source, DocumentType.INVOICE);

        expect(copy.DueDate).toBe(moment().add(15, 'day').format('YYYY-MM-DD'));
        expect(copy.PaymentTerms).toEqual({ Note: '15 days' });
    });

    test('recalculates an end of month due date instead of shifting it by a number of days', () => {
        const composer = composerWithCompany();
        const source = {
            ID: 'INV-2',
            IssueDate: '2025-03-10',
            DueDate: '2025-04-30',
            PaymentTerms: { Note: 'End of the month' },
        } as unknown as Invoice;

        const copy = composer.duplicate(source, DocumentType.INVOICE);

        expect(copy.DueDate).toBe(moment().add(1, 'month').endOf('month').format('YYYY-MM-DD'));
    });

    test('derives the payment terms from the source dates when the note is not recognised', () => {
        const composer = composerWithCompany();
        const source = {
            ID: 'INV-3',
            IssueDate: '2025-03-10',
            DueDate: '2025-04-30',
            PaymentTerms: { Note: 'Fin du mois' },
        } as unknown as Invoice;

        const copy = composer.duplicate(source, DocumentType.INVOICE);

        expect(copy.DueDate).toBe(moment().add(1, 'month').endOf('month').format('YYYY-MM-DD'));
    });

    test('keeps the payment period of the source when no payment terms match', () => {
        const composer = composerWithCompany();
        const source = {
            ID: 'INV-4',
            IssueDate: '2025-01-10',
            DueDate: '2025-02-24',
            PaymentTerms: { Note: '45 dagen netto' },
        } as unknown as Invoice;

        const copy = composer.duplicate(source, DocumentType.INVOICE);

        expect(copy.DueDate).toBe(moment().add(45, 'day').format('YYYY-MM-DD'));
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

describe('InvoiceComposer credit note from an invoice', () => {
    test('takes over the invoice content and links the credited invoice', () => {
        const composer = composerForCreditNotes();
        const invoice = creditableInvoice();

        const creditNote = composer.creditNoteFromInvoice(invoice);

        expect(creditNote.CreditNoteTypeCode).toBe(381);
        expect(creditNote.BuyerReference).toBe('PO-42');
        expect(creditNote.OrderReference).toEqual({ ID: '123' });
        expect(creditNote.AccountingCustomerParty).toEqual(invoice.AccountingCustomerParty);
        expect(creditNote.BillingReference).toEqual([{
            InvoiceDocumentReference: { ID: 'INV-20260002', IssueDate: '2026-02-26' },
        }]);
        expect(creditNote.CreditNoteLine).toEqual([{
            ID: '1',
            CreditedQuantity: { __unitCode: 'C62', value: 2 },
            LineExtensionAmount: { __currencyID: 'EUR', value: 200 },
            Item: { Name: 'Consultancy' },
            Price: { PriceAmount: { __currencyID: 'EUR', value: 100 } },
        }]);
    });

    test('starts as a new document instead of reusing the invoice identity', () => {
        const composer = composerForCreditNotes();
        const invoice = creditableInvoice();

        const creditNote = composer.creditNoteFromInvoice(invoice);

        expect(creditNote.ID).toBe('');
        expect(creditNote.IssueDate).toBe(moment().format('YYYY-MM-DD'));
        expect(creditNote.Note).toBe('VAT note');
        expect(creditNote.AdditionalDocumentReference[0].Attachment.EmbeddedDocumentBinaryObject.value).toBe('ZW1wdHk=');
        expect(creditNote.AdditionalDocumentReference[1].Attachment.EmbeddedDocumentBinaryObject.value).toBe('YSxiLGM=');
    });

    test('carries no due date, payment terms or payment means', () => {
        const composer = composerForCreditNotes();

        const creditNote = composer.creditNoteFromInvoice(creditableInvoice());

        expect(creditNote.DueDate).toBeUndefined();
        expect(creditNote.PaymentTerms).toBeUndefined();
        expect(creditNote.PaymentMeans).toBeUndefined();
    });

    test('leaves the invoice it was made from untouched', () => {
        const composer = composerForCreditNotes();
        const invoice = creditableInvoice();

        const creditNote = composer.creditNoteFromInvoice(invoice);
        creditNote.CreditNoteLine[0].Item.Name = 'Changed';

        expect(invoice.ID).toBe('INV-20260002');
        expect(invoice.InvoiceLine[0].Item.Name).toBe('Consultancy');
        expect(invoice.AdditionalDocumentReference[0].Attachment.EmbeddedDocumentBinaryObject.value).toBe('JVBERi0xLjc=');
    });

    test('creates a credit note with a placeholder order reference and without payment terms', () => {
        const composer = composerForCreditNotes('30_DAYS');

        const creditNote = composer.createCreditNote();

        expect(creditNote.OrderReference).toEqual({ ID: 'NA' });
        expect(creditNote.BuyerReference).toBeUndefined();
        expect(creditNote.PaymentTerms).toBeUndefined();
        expect(creditNote.PaymentMeans).toBeUndefined();
        expect(creditNote.Note).toBe('VAT note');
    });
});

describe('InvoiceComposer document type switching', () => {
    test('keeps the delivery details when switching type in both directions', () => {
        const composer = composerForCreditNotes();
        const invoice = creditableInvoice();
        invoice.Delivery = {
            ActualDeliveryDate: '2026-02-26',
            DeliveryLocation: { Address: { Country: { IdentificationCode: 'NL' } } },
        };

        const creditNote = composer.invoiceToCreditNote(invoice);
        expect(creditNote.Delivery).toEqual(invoice.Delivery);

        expect(composer.creditNoteToInvoice(creditNote).Delivery).toEqual(invoice.Delivery);
    });

    test('keeps the payment details of the invoice across a round trip', () => {
        const composer = composerForCreditNotes('30_DAYS');
        const invoice = creditableInvoice();

        const roundTripped = composer.creditNoteToInvoice(composer.invoiceToCreditNote(invoice));

        expect(roundTripped.DueDate).toBe('2026-03-28');
        expect(roundTripped.PaymentTerms).toEqual({ Note: '30 days' });
        expect(roundTripped.PaymentMeans).toEqual(invoice.PaymentMeans);
    });

    test('falls back to the company defaults when the credit note has no payment details', () => {
        const composer = composerForCreditNotes('NET_15');

        const invoice = composer.creditNoteToInvoice(composer.createCreditNote());

        expect(invoice.DueDate).toBe(moment().add(15, 'day').format('YYYY-MM-DD'));
        expect(invoice.PaymentTerms).toEqual({ Note: '15 days' });
        expect(invoice.PaymentMeans?.PaymentMeansCode?.value).toBe(30);
    });

    test('drops PaymentDueDate so the result is not rebuilt as a credit note', () => {
        const composer = composerForCreditNotes();
        const creditNote = composer.createCreditNote();
        creditNote.PaymentMeans = {
            PaymentMeansCode: { value: 30 },
            PaymentDueDate: '2026-03-28',
            PayeeFinancialAccount: { ID: 'BE68539007547034' },
        };

        const invoice = composer.creditNoteToInvoice(creditNote);

        expect(invoice.PaymentMeans).not.toHaveProperty('PaymentDueDate');
        expect(invoice.PaymentMeans?.PayeeFinancialAccount?.ID).toBe('BE68539007547034');
    });
});

function creditableInvoice(): Invoice {
    return {
        CustomizationID: 'urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0',
        ProfileID: 'urn:fdc:peppol.eu:2017:poacc:billing:01:1.0',
        ID: 'INV-20260002',
        IssueDate: '2026-02-26',
        DueDate: '2026-03-28',
        InvoiceTypeCode: 380,
        BuyerReference: 'PO-42',
        OrderReference: { ID: '123' },
        PaymentTerms: { Note: '30 days' },
        PaymentMeans: { PaymentID: '+++090/9337/55493+++', PayeeFinancialAccount: { ID: 'BE68539007547034' } },
        AccountingCustomerParty: { Party: { PartyName: { Name: 'Customer Ltd' } } },
        AdditionalDocumentReference: [
            { ID: GENERATED_INVOICE, Attachment: { EmbeddedDocumentBinaryObject: { value: 'JVBERi0xLjc=' } } },
            { ID: 'timesheet.csv', Attachment: { EmbeddedDocumentBinaryObject: { value: 'YSxiLGM=' } } },
        ],
        LegalMonetaryTotal: { PayableAmount: { __currencyID: 'EUR', value: 242 } },
        InvoiceLine: [{
            ID: '1',
            InvoicedQuantity: { __unitCode: 'C62', value: 2 },
            LineExtensionAmount: { __currencyID: 'EUR', value: 200 },
            Item: { Name: 'Consultancy' },
            Price: { PriceAmount: { __currencyID: 'EUR', value: 100 } },
        }],
    } as unknown as Invoice;
}

function creditNoteTranslations(key: string, options?: { days?: number }): string {
    if (key === 'invoice.modal.credit-note-vat-note') {
        return 'VAT note';
    }
    return key === 'paymentTerms.net' ? `${options?.days} days` : key;
}

function composerForCreditNotes(paymentTerms?: string): InvoiceComposer {
    const composer = Object.create(InvoiceComposer.prototype) as InvoiceComposer;
    Object.assign(composer, {
        // Object.create skips the class field initializers, so restate the ones the composer reads.
        paymentMeansCodes: [{ value: 10, __name: 'In Cash' }, { value: 30, __name: 'Credit Transfer' }],
        companyService: {
            myCompany: {
                paymentTerms,
                peppolId: '0208:1234567890',
                identifier: '0123456789',
                vatNumber: 'BE0123456789',
                name: 'Supplier Legal Name',
                displayName: 'Supplier Display Name',
                registeredOffice: { street: 'Main Street', houseNumber: '1', city: 'Brussels', postalCode: '1000' },
            },
        },
        i18n: { tr: (key: string, options?: { days?: number }) => creditNoteTranslations(key, options) },
    });
    return composer;
}

const paymentTermTranslations: Record<string, string> = {
    'paymentTerms.15_DAYS': '15 days',
    'paymentTerms.30_DAYS': '30 days',
    'paymentTerms.60_DAYS': '60 days',
    'paymentTerms.END_OF_NEXT_MONTH': 'End of the month',
};

function composerWithCompany(paymentTerms?: string): InvoiceComposer {
    const composer = Object.create(InvoiceComposer.prototype) as InvoiceComposer;
    Object.assign(composer, {
        companyService: { myCompany: { paymentTerms } },
        i18n: { tr: (key: string) => paymentTermTranslations[key] ?? key },
    });
    return composer;
}

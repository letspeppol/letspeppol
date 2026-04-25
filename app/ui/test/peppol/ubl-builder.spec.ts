import { describe, expect, test } from 'vitest';
import { parseInvoice, parseCreditNote } from '../../src/services/peppol/ubl-parser';
import { buildInvoiceXml, buildCreditNoteXml } from '../../src/services/peppol/ubl-builder';
import { sampleInvoiceXml, sampleCreditNoteXml, normalizeXml } from './ubl-test-utils';

// Round-trip tests: XML -> object -> XML using the string-based builder

describe('UBL string builder - Invoice', () => {
    test('parse -> buildInvoiceXml should match original XML (normalized)', () => {
        const invoiceObj = parseInvoice(sampleInvoiceXml);

        expect(invoiceObj.OrderReference?.ID).toBe('123123123');

        const rebuilt = buildInvoiceXml(invoiceObj);

        const originalNormalized = `<?xml version="1.0" encoding="UTF-8"?>` + normalizeXml(sampleInvoiceXml);
        const rebuiltNormalized = normalizeXml(rebuilt);

        expect(rebuiltNormalized).toBe(originalNormalized);
    });
});

describe('UBL string builder - CreditNote', () => {
    test('parse -> buildCreditNoteXml should match original XML (normalized)', () => {
        const creditNoteObj = parseCreditNote(sampleCreditNoteXml);

        expect(creditNoteObj.OrderReference?.ID).toBe('123123123');

        const rebuilt = buildCreditNoteXml(creditNoteObj);

        const originalNormalized = `<?xml version="1.0" encoding="UTF-8"?>` + normalizeXml(sampleCreditNoteXml);
        const rebuiltNormalized = normalizeXml(rebuilt);

        expect(rebuiltNormalized).toBe(originalNormalized);
    });
});

describe('UBL string builder - OrderReference omission', () => {
    test('buildInvoiceXml should not include cac:OrderReference when both ID and SalesOrderID are missing', () => {
        const invoiceObj = parseInvoice(sampleInvoiceXml);

        // Remove values; note: ID is required by the TypeScript type but can be absent in real data.
        (invoiceObj as any).OrderReference = { ID: undefined, SalesOrderID: undefined };

        const rebuilt = buildInvoiceXml(invoiceObj);
        expect(rebuilt).not.toContain('<cac:OrderReference>');
    });
});

describe('UBL string builder - BillingReference (credit notes)', () => {
    test('buildCreditNoteXml emits cac:BillingReference with InvoiceDocumentReference ID and IssueDate', () => {
        const creditNoteObj = parseCreditNote(sampleCreditNoteXml);
        creditNoteObj.BillingReference = [{
            InvoiceDocumentReference: { ID: 'INV-2026-0001', IssueDate: '2026-04-01' }
        }];

        const rebuilt = buildCreditNoteXml(creditNoteObj);
        expect(rebuilt).toContain(
            '<cac:BillingReference><cac:InvoiceDocumentReference><cbc:ID>INV-2026-0001</cbc:ID><cbc:IssueDate>2026-04-01</cbc:IssueDate></cac:InvoiceDocumentReference></cac:BillingReference>'
        );
    });

    test('buildCreditNoteXml omits cac:BillingReference when list is empty or missing ID', () => {
        const creditNoteObj = parseCreditNote(sampleCreditNoteXml);
        creditNoteObj.BillingReference = undefined;
        expect(buildCreditNoteXml(creditNoteObj)).not.toContain('<cac:BillingReference>');

        creditNoteObj.BillingReference = [{ InvoiceDocumentReference: { ID: '' } }];
        expect(buildCreditNoteXml(creditNoteObj)).not.toContain('<cac:BillingReference>');
    });
});

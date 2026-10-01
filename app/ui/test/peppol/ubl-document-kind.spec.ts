import { describe, expect, test } from 'vitest';
import { detectUblDocumentKind } from '../../src/services/peppol/ubl-parser';
import { sampleCreditNoteXml, sampleInvoiceXml } from './ubl-test-utils';

describe('detectUblDocumentKind', () => {
    test('reads the kind from the root element of a real document', () => {
        expect(detectUblDocumentKind(sampleInvoiceXml)).toBe('Invoice');
        expect(detectUblDocumentKind(sampleCreditNoteXml)).toBe('CreditNote');
    });

    test('recognises the root element regardless of namespace prefix or XML declaration', () => {
        const prefixed = `<?xml version="1.0" encoding="UTF-8"?>
            <ubl:CreditNote xmlns:ubl="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"
                            xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                <cbc:ID>CRN-1</cbc:ID>
            </ubl:CreditNote>`;

        expect(detectUblDocumentKind(prefixed)).toBe('CreditNote');
    });

    test('returns undefined for anything that is not an invoice or a credit note', () => {
        expect(detectUblDocumentKind('<Order><ID>1</ID></Order>')).toBeUndefined();
        expect(detectUblDocumentKind('not xml at all')).toBeUndefined();
        expect(detectUblDocumentKind('')).toBeUndefined();
        expect(detectUblDocumentKind(undefined as unknown as string)).toBeUndefined();
    });
});

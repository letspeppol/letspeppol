import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { parseDocument } from '../../src/parse.js';

describe('parseDocument function', () => {
  const invoiceXml = readFileSync('__tests__/fixtures/invoice-v1.xml', 'utf-8');
  const creditNoteXml = readFileSync('__tests__/fixtures/credit-note-v1.xml', 'utf-8');

  it ('should correctly parse sender and recipient from invoice XML', () => {
    const parsed = parseDocument(invoiceXml, '0208:0705969661');
    expect(parsed).toEqual({
      amount: 1656.25,
      docId: "Snippet1",
      docDetails: {
        userId: "0208:0705969661",
        counterPartyId: "9944:nl862637223B02",
        counterPartyName: "SupplierTradingName Ltd.",
        docType: "invoice",
      },
    });
  });
  it ('should correctly parse sender and recipient from credit note XML', () => {
    const parsed = parseDocument(creditNoteXml, '0208:0705969661');
    expect(parsed).toEqual({
      amount: 1656.25,
      docDetails: {
        userId: "0208:0705969661",
        counterPartyId: "0208:1023290711",
        counterPartyName: "SupplierTradingName Ltd.",
        docType: "credit-note",
      },
      docId: "Snippet1",
    });
  });
});

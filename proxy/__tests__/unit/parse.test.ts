import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { parseDocument } from '../../src/parse.js';

describe('parseDocument function', () => {
  const invoiceXml = readFileSync('__tests__/fixtures/invoice-v1.xml', 'utf-8');
  const creditNoteXml = readFileSync('__tests__/fixtures/credit-note-v1.xml', 'utf-8');

  it ('should correctly parse sender and recipient from invoice XML', () => {
    const parsed = parseDocument(invoiceXml, 'incoming');
    expect(parsed).toEqual({
      userId: "0208:0705969661",
      createdAt: expect.any(String),
      docType: "invoice",
      direction: "incoming",
      counterPartyId: "9944:nl862637223B02",
      counterPartyName: "SupplierTradingName Ltd.",
      docId: "Snippet1",
      dueDate: "2025-12-01",
      paymentTerms: "Payment within 10 days, 2% discount",
      amount: 1656.25,
      ubl: invoiceXml,
    });
  });
  it ('should correctly parse sender and recipient from credit note XML', () => {
    const parsed = parseDocument(creditNoteXml, 'incoming');
    expect(parsed).toEqual({
      userId: "0208:0705969661",
      createdAt: expect.any(String),
      docType: "credit-note",
      direction: "incoming",
      counterPartyId: "0208:1023290711",
      counterPartyName: "SupplierTradingName Ltd.",
      docId: "Snippet1",
      dueDate: undefined,
      paymentTerms: undefined,
      amount: 1656.25,
      ubl: creditNoteXml,
    });
  });
});

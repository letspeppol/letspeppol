import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { parseDocument } from '../../src/parse.js';

describe('parseDocument function', () => {
  const invoiceXml = readFileSync('__tests__/fixtures/invoice-v1.xml', 'utf-8');
  const creditNoteXml = readFileSync('__tests__/fixtures/credit-note-v1.xml', 'utf-8');

  it ('should correctly parse sender and recipient from invoice XML', () => {
    const parsed = parseDocument(invoiceXml);
    expect(parsed).toEqual({
      amount: 1656.25,
      docId: "Snippet1",
      recipient: "0208:0705969661",
      recipientName: "BuyerTradingName AS",
      sender: "9944:nl862637223B02",
      senderName: "SupplierTradingName Ltd.",
      docType: "Invoice",
    });
  });
  it ('should correctly parse sender and recipient from credit note XML', () => {
    const parsed = parseDocument(creditNoteXml);
    expect(parsed).toEqual({
      amount: 1656.25,
      docId: "Snippet1",
      recipient: "0208:0705969661",
      recipientName: "BuyerTradingName AS",
      sender: "0208:1023290711",
      senderName: "SupplierTradingName Ltd.",
      docType: "CreditNote",
    });
  });
});

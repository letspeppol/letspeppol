import { describe, it, expect } from 'vitest';
import { generateInvoicePdf } from '../../../src/invoice/pdf/invoice-pdf';
import { Invoice } from '../../../src/peppol/ubl';

function buildMinimalInvoice(): Invoice {
  return {
    CustomizationID: '1.0',
    ProfileID: 'profile',
    ID: 'TEST-1',
    IssueDate: '2025-01-01',
    BillingReference: [{ InvoiceDocumentReference: { ID: 'REF-1' } }],
    AccountingSupplierParty: {
      Party: {
        PartyName: { Name: 'Supplier Co' },
        PostalAddress: { StreetName: 'Main St 1', CityName: 'Town', PostalZone: '1234' },
        PartyTaxScheme: { CompanyID: { value: 'SUP123' } }
      }
    },
    AccountingCustomerParty: {
      Party: {
        PartyName: { Name: 'Customer Co' },
        PostalAddress: { StreetName: 'Second St 2', CityName: 'City', PostalZone: '5678' },
        PartyTaxScheme: { CompanyID: { value: 'CUS987' } }
      }
    },
    LegalMonetaryTotal: {
      PayableAmount: { value: 10, __currencyID: 'EUR' },
      TaxExclusiveAmount: { value: 8, __currencyID: 'EUR' }
    },
    InvoiceTypeCode: 380,
    InvoiceLine: [
      {
        ID: '1',
        LineExtensionAmount: { value: 8, __currencyID: 'EUR' },
        Item: { Name: 'Item 1', Description: 'Desc' },
        Price: { PriceAmount: { value: 4, __currencyID: 'EUR' } },
        InvoicedQuantity: { value: 2 }
      }
    ],
    TaxTotal: [{ TaxAmount: { value: 2, __currencyID: 'EUR' } }]
  } as Invoice; // Casting for simplicity in test
}

describe('invoice pdf generation', () => {
  it('generates a jsPDF instance without throwing', () => {
    const invoice = buildMinimalInvoice();
    const pdf = generateInvoicePdf(invoice);
    expect(pdf).toBeTruthy();
    expect(typeof (pdf as any).getNumberOfPages).toBe('function');
    expect((pdf as any).getNumberOfPages()).toBeGreaterThan(0);
  });
});


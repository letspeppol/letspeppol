import {describe, expect, test} from 'vitest';
import {isInvoiceValid, isPaymentInfoComplete} from '../../src/invoice/edit/invoice-validation';
import type {CreditNote, Invoice, UBLLine} from '../../src/services/peppol/ubl';

function createInvoice(): Invoice {
    return {
        BuyerReference: 'BUYER-REF',
        DueDate: '2026-10-06',
        IssueDate: '2026-09-06',
        AccountingCustomerParty: {
            Party: {
                PartyName: {Name: 'Customer'},
                PartyLegalEntity: {RegistrationName: 'Customer NV'},
            },
        },
        LegalMonetaryTotal: {
            LineExtensionAmount: {value: 100},
            PayableAmount: {value: 100},
        },
    } as Invoice;
}

function valid(invoice = createInvoice(), lines: UBLLine[] = []): boolean {
    return isInvoiceValid(invoice, lines, true);
}

describe('invoice send validation', () => {
    test('accepts either a buyer reference or an order reference', () => {
        const orderReferenceInvoice = createInvoice();
        orderReferenceInvoice.BuyerReference = undefined;
        orderReferenceInvoice.OrderReference = {ID: 'ORDER-REF'};

        expect(valid()).toBe(true);
        expect(valid(orderReferenceInvoice)).toBe(true);

        const noReferenceInvoice = createInvoice();
        noReferenceInvoice.BuyerReference = undefined;
        expect(valid(noReferenceInvoice)).toBe(false);
    });

    test('allows payment terms instead of a due date', () => {
        const invoice = createInvoice();
        invoice.DueDate = undefined;
        invoice.PaymentTerms = {Note: 'Pay within 30 days'};

        expect(valid(invoice)).toBe(true);

        invoice.PaymentTerms = undefined;
        expect(valid(invoice)).toBe(false);
    });

    test('allows a credit note without a due date or payment terms', () => {
        const creditNote = createInvoice() as CreditNote;
        creditNote.DueDate = undefined;
        creditNote.PaymentTerms = undefined;
        creditNote.CreditNoteTypeCode = 381;

        expect(valid(creditNote)).toBe(true);
    });

    test('allows no customer party identification', () => {
        const invoice = createInvoice();

        expect(valid(invoice)).toBe(true);
    });

    test('rejects an incomplete customer party identification when one is provided', () => {
        const invoice = createInvoice();

        invoice.AccountingCustomerParty.Party.PartyIdentification = [{ID: {value: ''}}];
        expect(valid(invoice)).toBe(false);

        invoice.AccountingCustomerParty.Party.PartyIdentification = [{ID: {value: '0208:123456789'}}];
        expect(valid(invoice)).toBe(true);
    });

    test('requires a customer legal entity and its registration name', () => {
        const invoice = createInvoice();
        invoice.AccountingCustomerParty.Party.PartyLegalEntity = undefined;
        expect(valid(invoice)).toBe(false);

        invoice.AccountingCustomerParty.Party.PartyLegalEntity = {};
        expect(valid(invoice)).toBe(false);

        invoice.AccountingCustomerParty.Party.PartyLegalEntity.RegistrationName = 'Customer NV';
        expect(valid(invoice)).toBe(true);
    });

    test('allows a customer party without a party name', () => {
        const invoice = createInvoice();
        invoice.AccountingCustomerParty.Party.PartyName = undefined;

        expect(valid(invoice)).toBe(true);
    });

    test('requires delivery details only for K tax-category lines', () => {
        const reverseChargeLine = {
            Item: {ClassifiedTaxCategory: {ID: 'K'}},
        } as UBLLine;
        const invoice = createInvoice();

        expect(valid(invoice)).toBe(true);
        expect(valid(invoice, [reverseChargeLine])).toBe(false);

        invoice.Delivery = {
            ActualDeliveryDate: '2026-09-06',
            DeliveryLocation: {Address: {Country: {IdentificationCode: 'BE'}}},
        };
        expect(valid(invoice, [reverseChargeLine])).toBe(true);
    });

    test('treats payment information as optional, except for transfers without an account', () => {
        expect(isPaymentInfoComplete(undefined)).toBe(true);
        expect(isPaymentInfoComplete({PaymentMeansCode: {value: 58}})).toBe(true);
        expect(isPaymentInfoComplete({PaymentMeansCode: {value: 30}})).toBe(false);
        expect(isPaymentInfoComplete({
            PaymentMeansCode: {value: 30},
            PayeeFinancialAccount: {ID: 'BE71096123456769'},
        })).toBe(true);

        expect(isInvoiceValid(createInvoice(), [], false)).toBe(false);
        expect(isInvoiceValid(createInvoice(), [], true)).toBe(true);
    });
});

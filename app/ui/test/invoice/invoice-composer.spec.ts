import { describe, expect, test } from 'vitest';
import { InvoiceComposer } from '../../src/invoice/invoice-composer';

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
});

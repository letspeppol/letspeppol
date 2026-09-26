import {beforeEach, describe, expect, test, vi} from 'vitest';

const invoiceContext = vi.hoisted(() => ({
    selectedInvoice: undefined as {
        AccountingCustomerParty?: {
            Party?: {
                PartyLegalEntity?: {RegistrationName?: string};
                PartyName?: {Name?: string};
            };
        };
    } | undefined,
}));

vi.mock('@aurelia/kernel', async importOriginal => ({
    ...await importOriginal<typeof import('@aurelia/kernel')>(),
    resolve: () => invoiceContext,
}));

import {CustomerInfo} from '../../src/invoice/edit/components/tiles/customer-info';

describe('CustomerInfo completion', () => {
    beforeEach(() => {
        invoiceContext.selectedInvoice = undefined;
    });

    test('uses the required legal registration name instead of the optional trading name', () => {
        invoiceContext.selectedInvoice = {
            AccountingCustomerParty: {
                Party: {
                    PartyLegalEntity: {RegistrationName: 'Customer NV'},
                },
            },
        };

        expect(new CustomerInfo().isCustomerInfoComplete).toBe(true);
    });

    test('is incomplete when the legal registration name is absent', () => {
        invoiceContext.selectedInvoice = {
            AccountingCustomerParty: {
                Party: {
                    PartyName: {Name: 'Optional trading name'},
                },
            },
        };

        expect(new CustomerInfo().isCustomerInfoComplete).toBe(false);
    });
});

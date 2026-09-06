import {beforeEach, describe, expect, it, vi} from 'vitest';
import {Account} from '../../src/account/account';

interface AccountHarness {
    company: {peppolActive: boolean};
    companyService: {myCompany: {peppolActive: boolean}};
    registrationService: {
        registerCompany: ReturnType<typeof vi.fn>;
        unregisterCompany: ReturnType<typeof vi.fn>;
    };
    ea: {publish: ReturnType<typeof vi.fn>};
    i18n: {tr: (key: string) => string};
    registerOnPeppol: () => Promise<void>;
    unregisterFromPeppol: () => Promise<void>;
}

function createAccountHarness(): AccountHarness {
    const account = Object.create(Account.prototype) as AccountHarness;
    account.company = {peppolActive: false};
    account.companyService = {myCompany: {peppolActive: false}};
    account.registrationService = {
        registerCompany: vi.fn(),
        unregisterCompany: vi.fn(),
    };
    account.ea = {publish: vi.fn()};
    account.i18n = {tr: (key: string) => key};
    return account;
}

describe('Account Peppol status changes', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    it('updates the current view and cached company after registration', async () => {
        const account = createAccountHarness();
        account.registrationService.registerCompany.mockResolvedValue(true);

        await account.registerOnPeppol();

        expect(account.company.peppolActive).toBe(true);
        expect(account.companyService.myCompany.peppolActive).toBe(true);
        expect(localStorage.getItem('peppolActive')).toBe('true');
        expect(account.ea.publish).toHaveBeenCalledWith('account:peppol-status-changed', true);
    });

    it('updates the current view and cached company after unregistration', async () => {
        const account = createAccountHarness();
        account.company.peppolActive = true;
        account.companyService.myCompany.peppolActive = true;
        account.registrationService.unregisterCompany.mockResolvedValue(false);

        await account.unregisterFromPeppol();

        expect(account.company.peppolActive).toBe(false);
        expect(account.companyService.myCompany.peppolActive).toBe(false);
        expect(localStorage.getItem('peppolActive')).toBe('false');
        expect(account.ea.publish).toHaveBeenCalledWith('account:peppol-status-changed', false);
    });
});

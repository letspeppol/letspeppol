import {describe, expect, it, vi} from 'vitest';
import {Account} from '../../src/account/account';
import {AccountService, CurrentAccount} from '../../src/services/kyc/account-service';

interface AccountHarness {
    ea: {publish: ReturnType<typeof vi.fn>};
    i18n: {tr: (key: string) => string};
    accountService: {getCurrentAccount: ReturnType<typeof vi.fn>};
    companyService: {myCompany?: {vatNumber?: string}; getAndSetMyCompanyForToken: ReturnType<typeof vi.fn>};
    ownershipService: {hasCompany: boolean};
    vatDisplay: {mode: string; setMode: ReturnType<typeof vi.fn>};
    currentAccount?: CurrentAccount;
    vatMode: string;
    showVatDisplaySettings: boolean;
    accountRequest: number;
    refreshCurrentAccount: () => Promise<void>;
    loadVatNumber: () => Promise<void>;
    unbinding: () => void;
}

function createAccountHarness(company?: {vatNumber?: string}): AccountHarness {
    const account = Object.create(Account.prototype) as AccountHarness;
    account.ea = {publish: vi.fn()};
    account.i18n = {tr: (key: string) => key};
    account.accountService = {getCurrentAccount: vi.fn().mockResolvedValue({name: 'Current User', email: 'user@example.test'})};
    account.companyService = {myCompany: company, getAndSetMyCompanyForToken: vi.fn()};
    account.ownershipService = {hasCompany: company !== undefined};
    account.vatDisplay = {mode: 'excl', setMode: vi.fn()};
    account.accountRequest = 0;
    return account;
}

describe('Account identity', () => {
    it('loads the signed-in account from the account endpoint', async () => {
        const get = vi.fn().mockResolvedValue({json: async () => ({name: 'Current User', email: 'user@example.test'})});
        const service = Object.create(AccountService.prototype) as AccountService;
        Object.assign(service, {kycApi: {httpClient: {get}}});

        expect(await service.getCurrentAccount()).toEqual({name: 'Current User', email: 'user@example.test'});
        expect(get).toHaveBeenCalledWith('/sapi/account');
    });

    it('shows the signed-in user', async () => {
        const account = createAccountHarness({vatNumber: 'BE0123456789'});

        await account.refreshCurrentAccount();

        expect(account.currentAccount).toEqual({name: 'Current User', email: 'user@example.test'});
    });

    it('clears the account fields on a failed refresh', async () => {
        const account = createAccountHarness({vatNumber: 'BE0123456789'});
        account.currentAccount = {name: 'Previous User', email: 'previous@example.test'};
        account.accountService.getCurrentAccount.mockRejectedValue(new Error('Unavailable'));

        await account.refreshCurrentAccount();

        expect(account.currentAccount).toBeUndefined();
        expect(account.ea.publish).toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.load-failed'}));
    });

    it('ignores a response after leaving the account page', async () => {
        const account = createAccountHarness({vatNumber: 'BE0123456789'});
        let complete: (value: CurrentAccount) => void;
        account.accountService.getCurrentAccount.mockReturnValue(new Promise(resolve => { complete = resolve; }));
        const pending = account.refreshCurrentAccount();
        account.unbinding();

        complete({name: 'Previous User', email: 'previous@example.test'});
        await pending;

        expect(account.currentAccount).toBeUndefined();
    });
});

describe('Account amount display', () => {
    it('applies the choice at once for a company with a VAT number', async () => {
        const account = createAccountHarness({vatNumber: 'BE0123456789'});

        await account.loadVatNumber();
        account.vatMode = 'incl';

        expect(account.showVatDisplaySettings).toBe(true);
        expect(account.vatDisplay.setMode).toHaveBeenCalledWith('incl');
    });

    it('is not offered for a company without a VAT number', async () => {
        const account = createAccountHarness({});

        await account.loadVatNumber();
        account.vatMode = 'excl';

        expect(account.showVatDisplaySettings).toBe(false);
        expect(account.vatMode).toBe('incl');
        expect(account.vatDisplay.setMode).not.toHaveBeenCalled();
    });

    it('does not look up a company for an account without one', async () => {
        const account = createAccountHarness();

        await account.loadVatNumber();

        expect(account.companyService.getAndSetMyCompanyForToken).not.toHaveBeenCalled();
        expect(account.showVatDisplaySettings).toBe(false);
    });
});

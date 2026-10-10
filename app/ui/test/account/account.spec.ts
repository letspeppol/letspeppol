import {beforeEach, describe, expect, it, vi} from 'vitest';
import {Account} from '../../src/account/account';
import {PeppolRegistrationDto} from '../../src/services/app/company-service';

interface AccountHarness {
    company: {peppolActive: boolean};
    companyService: {
        myCompany: {peppolActive: boolean};
        getPeppolRegistration: ReturnType<typeof vi.fn>;
    };
    registrationService: {
        registerCompany: ReturnType<typeof vi.fn>;
        unregisterCompany: ReturnType<typeof vi.fn>;
    };
    ea: {publish: ReturnType<typeof vi.fn>};
    i18n: {tr: (key: string) => string};
    registerOnPeppol: () => Promise<void>;
    unregisterFromPeppol: () => Promise<void>;
    getCompany: () => Promise<void>;
    refreshPeppolRegistration: () => Promise<void>;
    accessPointName: string;
    accessPointLoading: boolean;
    registrationRequest: number;
    vatDisplay: {mode: string};
}

function createAccountHarness(): AccountHarness {
    const account = Object.create(Account.prototype) as AccountHarness;
    account.company = {peppolActive: false};
    account.companyService = {
        myCompany: {peppolActive: false},
        getPeppolRegistration: vi.fn().mockResolvedValue({
            peppolId: '0208:0123456789', peppolActive: true, accessPoint: 'RECOMMAND',
        }),
    };
    account.registrationService = {
        registerCompany: vi.fn(),
        unregisterCompany: vi.fn(),
    };
    account.ea = {publish: vi.fn()};
    account.i18n = {tr: (key: string) => key};
    account.accessPointLoading = true;
    account.registrationRequest = 0;
    account.vatDisplay = {mode: 'excl'};
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
        expect(account.companyService.getPeppolRegistration).toHaveBeenCalledOnce();
        expect(account.accessPointName).toBe('Recommand');
    });

    it('updates the current view and cached company after unregistration', async () => {
        const account = createAccountHarness();
        account.company.peppolActive = true;
        account.companyService.myCompany.peppolActive = true;
        account.registrationService.unregisterCompany.mockResolvedValue(false);
        account.companyService.getPeppolRegistration.mockResolvedValue({
            peppolId: '0208:0123456789', peppolActive: false, accessPoint: 'NONE',
        });

        await account.unregisterFromPeppol();

        expect(account.company.peppolActive).toBe(false);
        expect(account.companyService.myCompany.peppolActive).toBe(false);
        expect(localStorage.getItem('peppolActive')).toBe('false');
        expect(account.ea.publish).toHaveBeenCalledWith('account:peppol-status-changed', false);
        expect(account.accessPointName).toBe('account.access-point-none');
    });

    it('loads the actual provider even when the company profile is already cached', async () => {
        const account = createAccountHarness();
        account.companyService.myCompany.peppolActive = true;
        account.companyService.getPeppolRegistration.mockResolvedValue({
            peppolId: '0208:0123456789', peppolActive: true, accessPoint: 'SCRADA',
        });

        await account.getCompany();

        expect(account.companyService.getPeppolRegistration).toHaveBeenCalledOnce();
        expect(account.accessPointName).toBe('Scrada');
    });

    it('keeps the account usable when provider lookup fails', async () => {
        const account = createAccountHarness();
        account.companyService.myCompany.peppolActive = true;
        account.companyService.getPeppolRegistration.mockRejectedValue(new Error('Proxy unavailable'));

        await account.getCompany();

        expect(account.company.peppolActive).toBe(true);
        expect(account.accessPointName).toBe('account.access-point-unavailable');
        expect(account.ea.publish).not.toHaveBeenCalled();
    });

    it('does not report registration as failed when the subsequent provider lookup fails', async () => {
        const account = createAccountHarness();
        account.registrationService.registerCompany.mockResolvedValue(true);
        account.companyService.getPeppolRegistration.mockRejectedValue(new Error('Proxy unavailable'));

        await account.registerOnPeppol();

        expect(account.company.peppolActive).toBe(true);
        expect(account.accessPointName).toBe('account.access-point-unavailable');
        expect(account.ea.publish).toHaveBeenCalledWith('alert', expect.objectContaining({
            text: 'alert.account.peppol-activated',
        }));
    });

    it('does not let an older lookup overwrite registration changes', async () => {
        const account = createAccountHarness();
        let completeOldLookup: (registration: PeppolRegistrationDto) => void;
        account.companyService.getPeppolRegistration.mockReturnValueOnce(new Promise<PeppolRegistrationDto>(resolve => {
            completeOldLookup = resolve;
        }));
        const oldLookup = account.refreshPeppolRegistration();
        expect(account.accessPointName).toBe('account.access-point-loading');
        account.companyService.getPeppolRegistration.mockResolvedValue({
            peppolId: '0208:0123456789', peppolActive: false, accessPoint: 'NONE',
        });

        await account.refreshPeppolRegistration();
        completeOldLookup({peppolId: '0208:0123456789', peppolActive: true, accessPoint: 'SCRADA'});
        await oldLookup;

        expect(account.accessPointName).toBe('account.access-point-none');
    });
});

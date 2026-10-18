import {beforeEach, describe, expect, it, vi} from 'vitest';
import {Account} from '../../src/account/account';
import {PeppolRegistrationDto, PeppolSettings} from '../../src/services/app/company-service';
import {AccountService, CurrentAccount} from '../../src/services/kyc/account-service';

interface AccountHarness {
    company: {peppolActive: boolean};
    companyService: {
        myCompany: {peppolActive: boolean};
        getPeppolRegistration: ReturnType<typeof vi.fn>;
        getPeppolSettings: ReturnType<typeof vi.fn>;
        updatePeppolSettings: ReturnType<typeof vi.fn>;
        updateCompany: ReturnType<typeof vi.fn>;
    };
    registrationService: {
        registerCompany: ReturnType<typeof vi.fn>;
        unregisterCompany: ReturnType<typeof vi.fn>;
    };
    ea: {publish: ReturnType<typeof vi.fn>};
    i18n: {tr: (key: string) => string};
    registerOnPeppol: () => Promise<boolean>;
    unregisterFromPeppol: () => Promise<boolean>;
    getCompany: () => Promise<void>;
    refreshPeppolRegistration: () => Promise<void>;
    accessPointName: string;
    accessPointLoading: boolean;
    registrationRequest: number;
    vatDisplay: {mode: string; setMode: ReturnType<typeof vi.fn>};
    ownershipService: {getCurrentOwnershipType: ReturnType<typeof vi.fn>};
    peppolSettings?: PeppolSettings;
    initialPeppolSettings?: PeppolSettings;
    peppolSettingsFailed: boolean;
    savePeppolSettings: () => Promise<boolean>;
    saveAccount: () => Promise<void>;
    accountSaving: boolean;
    changeReceiving: (event: Event) => void;
    changeSending: (event: Event) => void;
    sendingAllowed: boolean;
    pendingSending?: boolean;
    cancelChanges: () => void;
    register: () => void;
    confirmationModalContext: {showConfirmationModal: ReturnType<typeof vi.fn>};
    accountService: {getCurrentAccount: ReturnType<typeof vi.fn>};
    currentAccount?: CurrentAccount;
    accountRequest: number;
    refreshCurrentAccount: () => Promise<void>;
    unbinding: () => void;
}

function createAccountHarness(): AccountHarness {
    const account = Object.create(Account.prototype) as AccountHarness;
    account.company = {peppolActive: false};
    account.companyService = {
        myCompany: {peppolActive: false},
        getPeppolRegistration: vi.fn().mockResolvedValue({
            peppolId: '0208:0123456789', peppolActive: true, accessPoint: 'RECOMMAND',
        }),
        getPeppolSettings: vi.fn().mockResolvedValue({receiveDocuments: false}),
        updatePeppolSettings: vi.fn(),
        updateCompany: vi.fn().mockResolvedValue(undefined),
    };
    account.registrationService = {
        registerCompany: vi.fn(),
        unregisterCompany: vi.fn(),
    };
    account.ea = {publish: vi.fn()};
    account.i18n = {tr: (key: string) => key};
    account.accessPointLoading = true;
    account.registrationRequest = 0;
    account.vatDisplay = {mode: 'excl', setMode: vi.fn()};
    account.ownershipService = {getCurrentOwnershipType: vi.fn().mockReturnValue('ADMIN')};
    account.accountSaving = false;
    account.confirmationModalContext = {showConfirmationModal: vi.fn()};
    account.accountRequest = 0;
    account.accountService = {getCurrentAccount: vi.fn().mockResolvedValue({name: 'Current User', email: 'user@example.test'})};
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
        expect(account.ea.publish).not.toHaveBeenCalledWith('alert', expect.objectContaining({
            text: 'alert.account.peppol-activation-failed',
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

    it('loads settings for a registered Recommand company', async () => {
        const account = createAccountHarness();

        await account.refreshPeppolRegistration();

        expect(account.peppolSettings).toEqual({receiveDocuments: false});
        expect(account.peppolSettingsFailed).toBe(false);
    });

    it('does not load Recommand settings for another provider', async () => {
        const account = createAccountHarness();
        account.companyService.getPeppolRegistration.mockResolvedValue({peppolActive: true, accessPoint: 'SCRADA'});

        await account.refreshPeppolRegistration();

        expect(account.companyService.getPeppolSettings).not.toHaveBeenCalled();
        expect(account.peppolSettings).toBeUndefined();
    });

    it('keeps the provider visible when settings fail to load', async () => {
        const account = createAccountHarness();
        account.companyService.getPeppolSettings.mockRejectedValue(new Error('Unavailable'));

        await account.refreshPeppolRegistration();

        expect(account.accessPointName).toBe('Recommand');
        expect(account.peppolSettingsFailed).toBe(true);
        expect(account.peppolSettings).toBeUndefined();
    });

    it('saves only changed settings without changing sending activation', async () => {
        const account = createAccountHarness();
        account.company.peppolActive = true;
        await account.refreshPeppolRegistration();
        account.peppolSettings.receiveDocuments = true;
        account.companyService.updatePeppolSettings.mockResolvedValue({receiveDocuments: true});

        await account.savePeppolSettings();

        expect(account.companyService.updatePeppolSettings).toHaveBeenCalledWith({receiveDocuments: true});
        expect(account.company.peppolActive).toBe(true);
        expect(account.initialPeppolSettings).toEqual({receiveDocuments: true});
    });

    it('does not update provider settings for non-administrators', async () => {
        const account = createAccountHarness();
        await account.refreshPeppolRegistration();
        account.ownershipService.getCurrentOwnershipType.mockReturnValue('USER');
        account.peppolSettings.receiveDocuments = true;

        await account.savePeppolSettings();

        expect(account.companyService.updatePeppolSettings).not.toHaveBeenCalled();
    });

    it('reloads remote settings after a failed update and explains a receiving conflict', async () => {
        const account = createAccountHarness();
        await account.refreshPeppolRegistration();
        account.peppolSettings.receiveDocuments = true;
        account.companyService.updatePeppolSettings.mockRejectedValue(new Response('', {status: 409}));

        await account.savePeppolSettings();

        expect(account.peppolSettings.receiveDocuments).toBe(false);
        expect(account.ea.publish).toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.receiving-conflict'}));
    });

    it('ignores stale settings after switching registration', async () => {
        const account = createAccountHarness();
        let complete: (settings: PeppolSettings) => void;
        account.companyService.getPeppolSettings.mockReturnValueOnce(new Promise(resolve => { complete = resolve; }));
        const first = account.refreshPeppolRegistration();
        await Promise.resolve();
        account.companyService.getPeppolRegistration.mockResolvedValue({peppolActive: false, accessPoint: 'NONE'});
        await account.refreshPeppolRegistration();

        complete({receiveDocuments: true});
        await first;

        expect(account.peppolSettings).toBeUndefined();
    });
});

describe('Peppol settings confirmations and saving', () => {
    it('saves company and Peppol settings through the main Save action', async () => {
        const account = createAccountHarness();
        await account.refreshPeppolRegistration();
        account.peppolSettings.receiveDocuments = true;
        account.companyService.updatePeppolSettings.mockResolvedValue({receiveDocuments: false});

        await account.saveAccount();

        expect(account.companyService.updateCompany).toHaveBeenCalledOnce();
        expect(account.companyService.updatePeppolSettings).toHaveBeenCalledWith({receiveDocuments: true});
        expect(account.ea.publish).toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.updated'}));
        expect(account.accountSaving).toBe(false);
    });

    it('does not show success when saving the Peppol settings fails', async () => {
        const account = createAccountHarness();
        await account.refreshPeppolRegistration();
        account.peppolSettings.receiveDocuments = true;
        account.companyService.updatePeppolSettings.mockRejectedValue(new Response('', {status: 409}));

        await account.saveAccount();

        expect(account.ea.publish).toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.receiving-conflict'}));
        expect(account.ea.publish).not.toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.updated'}));
        expect(account.ea.publish).toHaveBeenCalledWith('hideOverlay');
        expect(account.accountSaving).toBe(false);
    });

    it('confirms disabling receiving and waits for the main Save action', async () => {
        const account = createAccountHarness();
        account.company.peppolActive = true;
        account.companyService.getPeppolSettings.mockResolvedValue({receiveDocuments: true});
        await account.refreshPeppolRegistration();
        const input = document.createElement('input');
        input.type = 'checkbox';
        input.checked = false;

        account.changeReceiving({target: input} as unknown as Event);

        expect(input.checked).toBe(true);
        expect(account.peppolSettings.receiveDocuments).toBe(true);
        expect(account.confirmationModalContext.showConfirmationModal).toHaveBeenCalledWith(
            'account.confirm.receiving-disable-title', 'account.confirm.receiving-disable', expect.any(Function), undefined);
        expect(account.companyService.updatePeppolSettings).not.toHaveBeenCalled();
        const confirm = account.confirmationModalContext.showConfirmationModal.mock.calls[0][2];
        confirm();
        expect(account.peppolSettings.receiveDocuments).toBe(false);
        expect(account.company.peppolActive).toBe(true);
        expect(account.registrationService.unregisterCompany).not.toHaveBeenCalled();
        expect(account.companyService.updatePeppolSettings).not.toHaveBeenCalled();
        account.companyService.updatePeppolSettings.mockResolvedValue({...account.peppolSettings});

        await account.saveAccount();

        expect(account.companyService.updatePeppolSettings).toHaveBeenCalledWith({receiveDocuments: false});
    });

    it('stages onboarding activation and waits for the main Save action', async () => {
        const account = createAccountHarness();

        account.register();

        expect(account.confirmationModalContext.showConfirmationModal).toHaveBeenCalledWith(
            'account.allow-sending', 'account.confirm.sending-enable', expect.any(Function), undefined);
        account.confirmationModalContext.showConfirmationModal.mock.calls[0][2]();
        expect(account.pendingSending).toBe(true);
        expect(account.registrationService.registerCompany).not.toHaveBeenCalled();
        account.registrationService.registerCompany.mockResolvedValue(true);

        await account.saveAccount();

        expect(account.registrationService.registerCompany).toHaveBeenCalledOnce();
        expect(account.ea.publish).toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.updated'}));
    });

    it('stages unchecking sending after confirmation and removes it only on Save', async () => {
        const account = createAccountHarness();
        account.company.peppolActive = true;
        account.companyService.myCompany.peppolActive = true;
        account.companyService.getPeppolSettings.mockResolvedValue({receiveDocuments: true});
        await account.refreshPeppolRegistration();
        const input = document.createElement('input');
        input.type = 'checkbox';
        input.checked = false;

        account.changeSending({target: input} as unknown as Event);

        expect(input.checked).toBe(true);
        expect(account.sendingAllowed).toBe(true);
        expect(account.confirmationModalContext.showConfirmationModal).toHaveBeenCalledWith(
            'account.remove-sending', 'account.confirm.sending-disable', expect.any(Function), undefined);
        account.confirmationModalContext.showConfirmationModal.mock.calls[0][2]();
        expect(account.sendingAllowed).toBe(false);
        expect(account.company.peppolActive).toBe(true);
        expect(account.registrationService.unregisterCompany).not.toHaveBeenCalled();
        account.registrationService.unregisterCompany.mockResolvedValue(false);
        account.companyService.getPeppolRegistration.mockResolvedValue({peppolActive: false, accessPoint: 'NONE'});

        await account.saveAccount();

        expect(account.registrationService.unregisterCompany).toHaveBeenCalledOnce();
        expect(account.sendingAllowed).toBe(false);
        expect(account.peppolSettings).toBeUndefined();
        expect(account.companyService.updatePeppolSettings).not.toHaveBeenCalled();
    });

    it('uses Save to activate sending after checking the box and confirming', async () => {
        const account = createAccountHarness();
        const input = document.createElement('input');
        input.type = 'checkbox';
        input.checked = true;

        account.changeSending({target: input} as unknown as Event);
        expect(input.checked).toBe(false);
        account.confirmationModalContext.showConfirmationModal.mock.calls[0][2]();
        expect(account.sendingAllowed).toBe(true);
        expect(account.registrationService.registerCompany).not.toHaveBeenCalled();
        account.registrationService.registerCompany.mockResolvedValue(true);

        await account.saveAccount();

        expect(account.registrationService.registerCompany).toHaveBeenCalledOnce();
        expect(account.pendingSending).toBeUndefined();
        expect(account.sendingAllowed).toBe(true);
        expect(account.peppolSettings).toEqual({receiveDocuments: false});
    });

    it('cancels a staged sending change without unregistering the company', async () => {
        const account = createAccountHarness();
        account.company.peppolActive = true;
        account.companyService.myCompany.peppolActive = true;
        await account.refreshPeppolRegistration();
        account.pendingSending = false;

        account.cancelChanges();

        expect(account.sendingAllowed).toBe(true);
        expect(account.registrationService.unregisterCompany).not.toHaveBeenCalled();
    });

    it('does not report success or save dependent settings when removing sending fails', async () => {
        const account = createAccountHarness();
        account.company.peppolActive = true;
        await account.refreshPeppolRegistration();
        account.pendingSending = false;
        account.registrationService.unregisterCompany.mockRejectedValue(new Error('Unavailable'));

        await account.saveAccount();

        expect(account.ea.publish).toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.peppol-remove-failed'}));
        expect(account.ea.publish).not.toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.updated'}));
        expect(account.companyService.updatePeppolSettings).not.toHaveBeenCalled();
    });
});

describe('Account identity', () => {
    it('loads the signed-in account using the current-account endpoint', async () => {
        const get = vi.fn().mockResolvedValue({json: async () => ({
            directorName: 'Current User', directorEmail: 'user@example.test',
        })});
        const service = Object.create(AccountService.prototype) as AccountService;
        Object.assign(service, {kycApi: {httpClient: {get}}});

        expect(await service.getCurrentAccount()).toEqual({name: 'Current User', email: 'user@example.test'});
        expect(get).toHaveBeenCalledWith('/sapi/company/account');
    });

    it('shows the signed-in user independently of the company subscriber', async () => {
        const account = createAccountHarness();
        Object.assign(account.company, {subscriber: 'Contract Signer', subscriberEmail: 'signer@example.test'});

        await account.refreshCurrentAccount();

        expect(account.currentAccount).toEqual({name: 'Current User', email: 'user@example.test'});
    });

    it('clears the account fields on a failed refresh', async () => {
        const account = createAccountHarness();
        account.currentAccount = {name: 'Previous User', email: 'previous@example.test'};
        account.accountService.getCurrentAccount.mockRejectedValue(new Error('Unavailable'));

        await account.refreshCurrentAccount();

        expect(account.currentAccount).toBeUndefined();
        expect(account.ea.publish).toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.load-failed'}));
    });

    it('ignores a response after leaving the account page', async () => {
        const account = createAccountHarness();
        let complete: (value: CurrentAccount) => void;
        account.accountService.getCurrentAccount.mockReturnValue(new Promise(resolve => { complete = resolve; }));
        const pending = account.refreshCurrentAccount();
        account.unbinding();

        complete({name: 'Previous User', email: 'previous@example.test'});
        await pending;

        expect(account.currentAccount).toBeUndefined();
    });
});

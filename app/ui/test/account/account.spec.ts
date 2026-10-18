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
    peppolSettingsFailed: boolean;
    savePeppolSettings: (receiveDocuments: boolean) => Promise<boolean>;
    saveAccount: () => Promise<void>;
    accountSaving: boolean;
    changeReceiving: (event: Event) => void;
    changeSending: (event: Event) => void;
    sendingAllowed: boolean;
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
        account.companyService.updatePeppolSettings.mockResolvedValue({receiveDocuments: true});

        await account.savePeppolSettings(true);

        expect(account.companyService.updatePeppolSettings).toHaveBeenCalledWith({receiveDocuments: true});
        expect(account.company.peppolActive).toBe(true);
        expect(account.peppolSettings).toEqual({receiveDocuments: true});
    });

    it('does not update provider settings for non-administrators', async () => {
        const account = createAccountHarness();
        await account.refreshPeppolRegistration();
        account.ownershipService.getCurrentOwnershipType.mockReturnValue('USER');

        await account.savePeppolSettings(true);

        expect(account.companyService.updatePeppolSettings).not.toHaveBeenCalled();
    });

    it('reloads remote settings after a failed update and explains a receiving conflict', async () => {
        const account = createAccountHarness();
        await account.refreshPeppolRegistration();
        account.companyService.updatePeppolSettings.mockRejectedValue(new Response('', {status: 409}));

        await account.savePeppolSettings(true);

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
    it.each([true, false])('applies receiving=%s immediately after warning confirmation', async enabled => {
        const account = createAccountHarness();
        account.company.peppolActive = true;
        account.companyService.getPeppolSettings.mockResolvedValue({receiveDocuments: !enabled});
        await account.refreshPeppolRegistration();
        account.companyService.updatePeppolSettings.mockResolvedValue({receiveDocuments: enabled});
        const input = document.createElement('input');
        input.type = 'checkbox';
        input.checked = enabled;

        account.changeReceiving({target: input} as unknown as Event);

        expect(input.checked).toBe(!enabled);
        expect(account.peppolSettings.receiveDocuments).toBe(!enabled);
        const action = enabled ? 'enable' : 'disable';
        expect(account.confirmationModalContext.showConfirmationModal).toHaveBeenCalledWith(
            `account.confirm.receiving-${action}-title`, `account.confirm.receiving-${action}`, expect.any(Function), undefined, 'warning');
        expect(account.companyService.updatePeppolSettings).not.toHaveBeenCalled();

        await account.confirmationModalContext.showConfirmationModal.mock.calls[0][2]();

        expect(account.companyService.updatePeppolSettings).toHaveBeenCalledOnce();
        expect(account.companyService.updatePeppolSettings).toHaveBeenCalledWith({receiveDocuments: enabled});
        expect(account.peppolSettings.receiveDocuments).toBe(enabled);
        expect(account.companyService.updateCompany).not.toHaveBeenCalled();
        expect(account.registrationService.unregisterCompany).not.toHaveBeenCalled();
        expect(account.ea.publish).toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.peppol-settings-updated'}));
        expect(account.accountSaving).toBe(false);

        account.cancelChanges();
        expect(account.peppolSettings.receiveDocuments).toBe(enabled);
        await account.saveAccount();
        expect(account.companyService.updatePeppolSettings).toHaveBeenCalledOnce();
    });

    it('applies onboarding activation immediately after confirmation', async () => {
        const account = createAccountHarness();
        account.registrationService.registerCompany.mockResolvedValue(true);

        account.register();

        expect(account.confirmationModalContext.showConfirmationModal).toHaveBeenCalledWith(
            'account.allow-sending', 'account.confirm.sending-enable', expect.any(Function), undefined, 'warning');
        expect(account.registrationService.registerCompany).not.toHaveBeenCalled();

        await account.confirmationModalContext.showConfirmationModal.mock.calls[0][2]();

        expect(account.registrationService.registerCompany).toHaveBeenCalledOnce();
        expect(account.sendingAllowed).toBe(true);
        expect(account.companyService.updateCompany).not.toHaveBeenCalled();
        expect(account.ea.publish).toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.peppol-settings-updated'}));
    });

    it.each([true, false])('applies sending=%s immediately after warning confirmation', async enabled => {
        const account = createAccountHarness();
        account.company.peppolActive = !enabled;
        account.companyService.myCompany.peppolActive = !enabled;
        account.companyService.getPeppolRegistration.mockResolvedValue({peppolActive: !enabled, accessPoint: enabled ? 'NONE' : 'RECOMMAND'});
        await account.refreshPeppolRegistration();
        account.registrationService.registerCompany.mockResolvedValue(true);
        account.registrationService.unregisterCompany.mockResolvedValue(false);
        account.companyService.getPeppolRegistration.mockResolvedValue({peppolActive: enabled, accessPoint: enabled ? 'RECOMMAND' : 'NONE'});
        const input = document.createElement('input');
        input.type = 'checkbox';
        input.checked = enabled;

        account.changeSending({target: input} as unknown as Event);

        expect(input.checked).toBe(!enabled);
        expect(account.sendingAllowed).toBe(!enabled);
        expect(account.registrationService.registerCompany).not.toHaveBeenCalled();
        expect(account.registrationService.unregisterCompany).not.toHaveBeenCalled();
        expect(account.confirmationModalContext.showConfirmationModal).toHaveBeenCalledWith(
            enabled ? 'account.allow-sending' : 'account.remove-sending',
            enabled ? 'account.confirm.sending-enable' : 'account.confirm.sending-disable', expect.any(Function), undefined, 'warning');

        await account.confirmationModalContext.showConfirmationModal.mock.calls[0][2]();

        expect(enabled ? account.registrationService.registerCompany : account.registrationService.unregisterCompany).toHaveBeenCalledOnce();
        expect(account.sendingAllowed).toBe(enabled);
        expect(account.companyService.updateCompany).not.toHaveBeenCalled();
        expect(account.companyService.updatePeppolSettings).not.toHaveBeenCalled();
        expect(account.peppolSettings).toEqual(enabled ? {receiveDocuments: false} : undefined);
        account.cancelChanges();
        expect(account.sendingAllowed).toBe(enabled);
        await account.saveAccount();
        expect(enabled ? account.registrationService.registerCompany : account.registrationService.unregisterCompany).toHaveBeenCalledOnce();
    });

    it('keeps the saved receiving state and reports a conflict if confirmation fails', async () => {
        const account = createAccountHarness();
        await account.refreshPeppolRegistration();
        account.companyService.updatePeppolSettings.mockRejectedValue(new Response('', {status: 409}));
        account.changeReceiving({target: {checked: true}} as unknown as Event);

        await account.confirmationModalContext.showConfirmationModal.mock.calls[0][2]();

        expect(account.peppolSettings.receiveDocuments).toBe(false);
        expect(account.ea.publish).toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.receiving-conflict'}));
        expect(account.ea.publish).not.toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.peppol-settings-updated'}));
        expect(account.ea.publish).toHaveBeenCalledWith('hideOverlay');
        expect(account.accountSaving).toBe(false);
    });

    it('keeps sending active and reports an error if removal fails', async () => {
        const account = createAccountHarness();
        await account.refreshPeppolRegistration();
        account.registrationService.unregisterCompany.mockRejectedValue(new Error('Unavailable'));
        account.changeSending({target: {checked: false}} as unknown as Event);

        await account.confirmationModalContext.showConfirmationModal.mock.calls[0][2]();

        expect(account.sendingAllowed).toBe(true);
        expect(account.ea.publish).toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.peppol-remove-failed'}));
        expect(account.ea.publish).not.toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.account.peppol-settings-updated'}));
        expect(account.accountSaving).toBe(false);
    });

    it('prevents duplicate writes and keeps the confirmed checkbox state until the server responds', async () => {
        const account = createAccountHarness();
        await account.refreshPeppolRegistration();
        let complete: (settings: PeppolSettings) => void;
        account.companyService.updatePeppolSettings.mockReturnValue(new Promise(resolve => { complete = resolve; }));
        account.changeReceiving({target: {checked: true}} as unknown as Event);
        const confirm = account.confirmationModalContext.showConfirmationModal.mock.calls[0][2];

        const pending = confirm();
        await confirm();
        await account.saveAccount();

        expect(account.accountSaving).toBe(true);
        expect(account.peppolSettings.receiveDocuments).toBe(false);
        expect(account.companyService.updatePeppolSettings).toHaveBeenCalledOnce();
        expect(account.companyService.updateCompany).not.toHaveBeenCalled();
        complete({receiveDocuments: true});
        await pending;
        expect(account.accountSaving).toBe(false);
        expect(account.peppolSettings.receiveDocuments).toBe(true);
    });

    it('does not apply a confirmation for a company that is no longer displayed', async () => {
        const account = createAccountHarness();
        account.register();
        account.company = {peppolActive: false};

        await account.confirmationModalContext.showConfirmationModal.mock.calls[0][2]();

        expect(account.registrationService.registerCompany).not.toHaveBeenCalled();
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

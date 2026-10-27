import {beforeEach, describe, expect, it, vi} from 'vitest';
import {CompanySettings} from '../../src/settings/company-settings';
import {PeppolRegistrationDto, PeppolSettings} from '../../src/services/app/company-service';
import {createFixture} from '@aurelia/testing';
import {I18nConfiguration} from '@aurelia/i18n';
import settingsTemplate from '../../src/settings/company-settings.html?raw';
import en from '../../src/app/locale/translation_en.json';

interface AccountHarness {
    company: {peppolActive: boolean};
    companyService: {
        myCompany: {peppolActive: boolean};
        getPeppolRegistration: ReturnType<typeof vi.fn>;
        getPeppolSettings: ReturnType<typeof vi.fn>;
        getReceivingAvailability: ReturnType<typeof vi.fn>;
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
    refreshReceivingSettings: () => Promise<void>;
    accessPointName: string;
    accessPointLoading: boolean;
    registrationRequest: number;
    warningKey?: string;
    alreadyRegisteredProvider: string;
    ownershipService: {getCurrentOwnershipType: ReturnType<typeof vi.fn>; granted: {COMPANY_SETTINGS: boolean}};
    peppolSettings?: PeppolSettings;
    peppolSettingsFailed: boolean;
    receivingBlocked: boolean;
    canChangeReceiving: boolean;
    savePeppolSettings: (receiveDocuments: boolean) => Promise<boolean>;
    saveAccount: () => Promise<void>;
    accountSaving: boolean;
    changeReceiving: (event: Event) => void;
    changeSending: (event: Event) => void;
    sendingAllowed: boolean;
    supportsPeppolSettings: boolean;
    isScrada: boolean;
    cancelChanges: () => void;
    register: () => void;
    confirmationModalContext: {showConfirmationModal: ReturnType<typeof vi.fn>};
    unbinding: () => void;
}

function createAccountHarness(): AccountHarness {
    const account = Object.create(CompanySettings.prototype) as AccountHarness;
    account.company = {peppolActive: false};
    account.companyService = {
        myCompany: {peppolActive: false},
        getPeppolRegistration: vi.fn().mockResolvedValue({
            peppolId: '0208:0123456789', peppolActive: true, accessPoint: 'RECOMMAND',
        }),
        getPeppolSettings: vi.fn().mockResolvedValue({receiveDocuments: false}),
        getReceivingAvailability: vi.fn().mockResolvedValue({receiveDocuments: false}),
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
    account.ownershipService = {getCurrentOwnershipType: vi.fn().mockReturnValue('ADMIN'), granted: {COMPANY_SETTINGS: true}};
    account.accountSaving = false;
    account.confirmationModalContext = {showConfirmationModal: vi.fn()};
    return account;
}

async function mountPeppolCard(account: AccountHarness) {
    const template = document.createElement('template');
    template.innerHTML = settingsTemplate;
    const card = template.content.querySelector('[t="account.peppol-settings"]').closest('.card');
    const fixture = createFixture(
        `<div with.bind="account">${card.outerHTML}</div>`,
        class { account = account; },
        [I18nConfiguration.customize(options => {
            options.initOptions = {lng: 'en', resources: {en: {translation: en}}};
        })],
    );
    await fixture.started;
    return fixture;
}

describe('Company settings Peppol status changes', () => {
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

    it('clears the previous provider conflict after a successful registration retry', async () => {
        const account = createAccountHarness();
        account.registrationService.registerCompany
            .mockRejectedValueOnce(new Response('https://other-smp.example', {status: 409}))
            .mockResolvedValueOnce(true);

        expect(await account.registerOnPeppol()).toBe(false);
        expect(account.warningKey).toBe('account.registration-failed.contact-provider');
        expect(await account.registerOnPeppol()).toBe(true);
        expect(account.warningKey).toBeUndefined();
        expect(account.alreadyRegisteredProvider).toBe('');
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

    it('blocks receiving at another SMP and allows selection after a fresh check releases it', async () => {
        const account = createAccountHarness();
        account.companyService.getPeppolSettings.mockResolvedValueOnce({
            receiveDocuments: false, receivingSmpUrl: 'https://other-smp.example',
        });
        await account.refreshPeppolRegistration();

        expect(account.receivingBlocked).toBe(true);
        expect(account.canChangeReceiving).toBe(false);
        const input = {checked: true};
        account.changeReceiving({target: input} as unknown as Event);
        expect(input.checked).toBe(false);
        expect(account.confirmationModalContext.showConfirmationModal).not.toHaveBeenCalled();
        expect(await account.savePeppolSettings(true)).toBe(false);
        expect(account.companyService.updatePeppolSettings).not.toHaveBeenCalled();

        await account.refreshPeppolRegistration();

        expect(account.receivingBlocked).toBe(false);
        expect(account.canChangeReceiving).toBe(true);
        expect(account.peppolSettings.receiveDocuments).toBe(false);
        account.changeReceiving({target: {checked: true}} as unknown as Event);
        expect(account.confirmationModalContext.showConfirmationModal).toHaveBeenCalledOnce();
    });

    it('keeps receiving unavailable when refreshing the SMP check fails', async () => {
        const account = createAccountHarness();
        await account.refreshPeppolRegistration();
        account.companyService.getPeppolSettings.mockRejectedValue(new Error('Lookup unavailable'));

        await account.refreshPeppolRegistration();

        expect(account.canChangeReceiving).toBe(false);
        expect(account.peppolSettingsFailed).toBe(true);
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
        expect(account.companyService.getPeppolRegistration).toHaveBeenCalledOnce();
        expect(account.companyService.getPeppolSettings).toHaveBeenCalledTimes(2);
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
    it.each(['RECOMMAND', 'SCRADA'])('uses default %s for an unconnected company', async defaultAccessPoint => {
        const account = createAccountHarness();
        account.companyService.getPeppolRegistration.mockResolvedValue({
            peppolActive: false, accessPoint: 'NONE', defaultAccessPoint,
        });

        await account.refreshPeppolRegistration();

        expect(account.accessPointName).toBe('account.access-point-none');
        expect(account.sendingAllowed).toBe(false);
        expect(account.isScrada).toBe(defaultAccessPoint === 'SCRADA');
        expect(account.supportsPeppolSettings).toBe(defaultAccessPoint === 'RECOMMAND');
        expect(account.canChangeReceiving).toBe(defaultAccessPoint === 'RECOMMAND');
        expect(account.companyService.getPeppolSettings).not.toHaveBeenCalled();

        const fixture = await mountPeppolCard(account);
        const checkboxes = [...fixture.getBy('.card').querySelectorAll<HTMLInputElement>('input[type="checkbox"]')];
        expect(checkboxes.map(input => input.parentElement.textContent.trim())).toEqual(defaultAccessPoint === 'SCRADA'
            ? ['Send and receive Peppol documents'] : ['Send Peppol documents', 'Receive Peppol documents']);
        expect(checkboxes.every(input => !input.checked)).toBe(true);
        expect(checkboxes[0].disabled).toBe(false);
        if (defaultAccessPoint === 'RECOMMAND') {
            expect(checkboxes[1].disabled).toBe(false);
            expect(account.companyService.getReceivingAvailability).toHaveBeenCalledOnce();
        } else {
            expect(account.companyService.getReceivingAvailability).not.toHaveBeenCalled();
        }

        account.changeSending({target: {checked: true}} as unknown as Event);

        expect(account.confirmationModalContext.showConfirmationModal).toHaveBeenCalledWith(
            defaultAccessPoint === 'SCRADA' ? 'account.send-and-receive-peppol-documents' : 'account.allow-sending',
            defaultAccessPoint === 'SCRADA' ? 'account.confirm.sending-receiving-enable' : 'account.confirm.sending-enable',
            expect.any(Function), undefined, 'warning');
    });

    it('shows the receiving SMP before activation and unlocks receiving after refresh releases it', async () => {
        const account = createAccountHarness();
        account.companyService.getPeppolRegistration.mockResolvedValue({
            peppolActive: false, accessPoint: 'NONE', defaultAccessPoint: 'RECOMMAND',
        });
        account.companyService.getReceivingAvailability.mockResolvedValueOnce({
            receiveDocuments: false, receivingSmpUrl: 'https://other-smp.example',
        });
        await account.refreshPeppolRegistration();
        const fixture = await mountPeppolCard(account);
        const inputs = [...fixture.getBy('.card').querySelectorAll<HTMLInputElement>('input[type="checkbox"]')];

        expect(inputs).toHaveLength(2);
        expect(inputs[0].disabled).toBe(false);
        expect(inputs[1].disabled).toBe(true);
        expect(inputs[1].checked).toBe(false);
        expect(fixture.getBy('#peppol-receiving-provider').textContent).toContain('https://other-smp.example');
        expect(fixture.getBy('.card').querySelector('#peppol-received-document-types')).toBeNull();

        await account.refreshReceivingSettings();

        expect(account.canChangeReceiving).toBe(true);
        expect(account.sendingAllowed).toBe(false);
        expect(account.companyService.getReceivingAvailability).toHaveBeenCalledTimes(2);
        expect(account.companyService.getPeppolRegistration).toHaveBeenCalledOnce();
        expect(account.registrationService.registerCompany).not.toHaveBeenCalled();
    });

    it('keeps receiving disabled when the initial SMP lookup fails', async () => {
        const account = createAccountHarness();
        account.companyService.getPeppolRegistration.mockResolvedValue({
            peppolActive: false, accessPoint: 'NONE', defaultAccessPoint: 'RECOMMAND',
        });
        account.companyService.getReceivingAvailability.mockRejectedValue(new Error('Lookup unavailable'));

        await account.refreshPeppolRegistration();

        expect(account.canChangeReceiving).toBe(false);
        expect(account.peppolSettingsFailed).toBe(true);
        expect(account.registrationService.registerCompany).not.toHaveBeenCalled();
    });

    it('allows selecting receiving before sending and activates through the existing registration flow', async () => {
        const account = createAccountHarness();
        account.companyService.getPeppolRegistration.mockResolvedValueOnce({
            peppolActive: false, accessPoint: 'NONE', defaultAccessPoint: 'RECOMMAND',
        });
        await account.refreshPeppolRegistration();
        account.registrationService.registerCompany.mockResolvedValue(true);
        account.companyService.getPeppolSettings.mockResolvedValue({receiveDocuments: true});

        account.changeReceiving({target: {checked: true}} as unknown as Event);
        expect(account.registrationService.registerCompany).not.toHaveBeenCalled();
        await account.confirmationModalContext.showConfirmationModal.mock.calls[0][2]();

        expect(account.registrationService.registerCompany).toHaveBeenCalledOnce();
        expect(account.peppolSettings.receiveDocuments).toBe(true);
        expect(account.companyService.updatePeppolSettings).not.toHaveBeenCalled();
    });

    it.each(['RECOMMAND', 'SCRADA'])('uses current %s while connected even when the default differs', async accessPoint => {
        const account = createAccountHarness();
        account.companyService.getPeppolRegistration.mockResolvedValue({
            peppolActive: true, accessPoint,
            defaultAccessPoint: accessPoint === 'SCRADA' ? 'RECOMMAND' : 'SCRADA',
        });

        await account.refreshPeppolRegistration();

        expect(account.isScrada).toBe(accessPoint === 'SCRADA');
        expect(account.supportsPeppolSettings).toBe(accessPoint === 'RECOMMAND');
        expect(account.canChangeReceiving).toBe(accessPoint === 'RECOMMAND');
    });

    it('turns off the entire Scrada registration through the combined checkbox', async () => {
        const account = createAccountHarness();
        account.company.peppolActive = true;
        account.companyService.getPeppolRegistration.mockResolvedValue({peppolActive: true, accessPoint: 'SCRADA'});
        await account.refreshPeppolRegistration();
        account.registrationService.unregisterCompany.mockResolvedValue(false);
        account.companyService.getPeppolRegistration.mockResolvedValue({peppolActive: false, accessPoint: 'NONE'});
        const input = {checked: false};

        account.changeSending({target: input} as unknown as Event);

        expect(input.checked).toBe(true);
        expect(account.confirmationModalContext.showConfirmationModal).toHaveBeenCalledWith(
            'account.stop-sending-and-receiving', 'account.confirm.sending-disable', expect.any(Function), undefined, 'warning');
        expect(account.registrationService.unregisterCompany).not.toHaveBeenCalled();

        await account.confirmationModalContext.showConfirmationModal.mock.calls[0][2]();

        expect(account.registrationService.unregisterCompany).toHaveBeenCalledOnce();
        expect(account.sendingAllowed).toBe(false);
        expect(account.companyService.updatePeppolSettings).not.toHaveBeenCalled();
    });

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

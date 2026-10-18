import {CompanyDto, CompanyService, PeppolRegistrationDto, PeppolSettings} from "../services/app/company-service";
import {OwnershipService} from "../services/app/ownership-service";
import {resolve} from "@aurelia/kernel";
import {AlertType} from "../components/alert/alert";
import {IEventAggregator, IDisposable} from "aurelia";
import {RegistrationService} from "../services/kyc/registration-service";
import {AccountService, CurrentAccount} from "../services/kyc/account-service";
import {ChangePasswordModal} from "./change-password-modal";
import {ConfirmationModalContext} from "../components/confirmation/confirmation-modal-context";
import {validateEmail} from "../app/util/email-validation";
import {I18N} from "@aurelia/i18n";
import {getVatDisplayMode, hasVatNumber, IVatDisplay, VatDisplayMode} from "../services/app/vat-display-service";

const ACCESS_POINTS: Record<string, {name: string; url: string}> = {
    RECOMMAND: {name: 'Recommand', url: 'https://recommand.eu/'},
    SCRADA: {name: 'Scrada', url: 'https://www.scrada.be/'},
    E_INVOICE: {name: 'E-Invoice', url: 'https://e-invoice.be/'},
};

export class Account {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private sub?: IDisposable;
    private readonly companyService = resolve(CompanyService);
    private readonly ownershipService = resolve(OwnershipService);
    private readonly registrationService = resolve(RegistrationService);
    private readonly accountService = resolve(AccountService);
    private currentAccount?: CurrentAccount;
    private accountRequest = 0;
    private readonly confirmationModalContext = resolve(ConfirmationModalContext);
    private readonly i18n = resolve(I18N);
    private readonly vatDisplay = resolve(IVatDisplay);
    private company: CompanyDto;
    changePasswordModal: ChangePasswordModal;
    private warningKey;
    private alreadyRegisteredProvider = '';
    private initialVatDisplayMode: VatDisplayMode = this.vatDisplay.mode;
    private peppolRegistration?: PeppolRegistrationDto;
    private accessPointLoading = true;
    private registrationRequest = 0;
    private peppolSettings?: PeppolSettings;
    private peppolSettingsLoading = false;
    private peppolSettingsFailed = false;
    private accountSaving = false;

    get sendingAllowed(): boolean {
        return this.peppolRegistration?.peppolActive ?? !!this.company?.peppolActive;
    }

    changeSending(event: Event) {
        const input = event.target as HTMLInputElement;
        const enabled = input.checked;
        input.checked = this.sendingAllowed;
        this.confirmSending(enabled);
    }

    private confirmSending(enabled: boolean) {
        if (!this.canEditPeppolSettings || this.accountSaving || enabled === this.sendingAllowed) return;
        const company = this.company;
        this.confirmationModalContext.showConfirmationModal(
            this.i18n.tr(enabled ? 'account.allow-sending' : 'account.remove-sending'),
            this.i18n.tr(enabled ? 'account.confirm.sending-enable' : 'account.confirm.sending-disable'),
            () => {
                if (company !== this.company || enabled === this.sendingAllowed) return;
                return this.applyPeppolChange(() => enabled ? this.registerOnPeppol() : this.unregisterFromPeppol());
            },
            undefined,
            'warning'
        );
    }

    get canEditPeppolSettings(): boolean {
        return this.ownershipService.getCurrentOwnershipType() === 'ADMIN';
    }

    get supportsPeppolSettings(): boolean {
        return !!this.peppolRegistration?.peppolActive && this.peppolRegistration.accessPoint === 'RECOMMAND';
    }

    get accessPointName(): string {
        if (this.accessPointLoading) return this.i18n.tr('account.access-point-loading');
        const registration = this.peppolRegistration;
        if (!registration) return this.i18n.tr('account.access-point-unavailable');
        if (!registration.peppolActive || registration.accessPoint === 'NONE') {
            return this.i18n.tr('account.access-point-none');
        }
        return ACCESS_POINTS[registration.accessPoint]?.name
            || registration.accessPoint || this.i18n.tr('account.access-point-unavailable');
    }

    get accessPointUrl(): string | undefined {
        if (this.accessPointLoading || !this.peppolRegistration?.peppolActive) return undefined;
        return ACCESS_POINTS[this.peppolRegistration.accessPoint]?.url;
    }

    private async refreshPeppolRegistration() {
        const request = ++this.registrationRequest;
        this.accessPointLoading = true;
        this.peppolRegistration = undefined;
        this.peppolSettings = undefined;
        this.peppolSettingsFailed = false;
        try {
            const registration = await this.companyService.getPeppolRegistration();
            if (request !== this.registrationRequest) return;
            this.peppolRegistration = registration;
            this.accessPointLoading = false;
            if (this.supportsPeppolSettings) {
                this.peppolSettingsLoading = true;
                try {
                    const settings = await this.companyService.getPeppolSettings();
                    if (request === this.registrationRequest) {
                        this.peppolSettings = {...settings};
                    }
                } catch {
                    if (request === this.registrationRequest) this.peppolSettingsFailed = true;
                }
            }
        } catch {
            // Provider lookup failure must not prevent loading or updating the account.
        } finally {
            if (request === this.registrationRequest) {
                this.accessPointLoading = false;
                this.peppolSettingsLoading = false;
            }
        }
    }

    changeReceiving(event: Event) {
        const input = event.target as HTMLInputElement;
        const enabled = input.checked;
        const settings = this.peppolSettings;
        if (!settings) return;
        input.checked = settings.receiveDocuments;
        if (!this.sendingAllowed || !this.canEditPeppolSettings || this.accountSaving || settings.receiveDocuments === enabled) return;
        const action = enabled ? 'enable' : 'disable';
        this.confirmationModalContext.showConfirmationModal(
            this.i18n.tr(`account.confirm.receiving-${action}-title`),
            this.i18n.tr(`account.confirm.receiving-${action}`),
            () => {
                if (this.peppolSettings !== settings || !this.sendingAllowed) return;
                return this.applyPeppolChange(() => this.savePeppolSettings(enabled));
            },
            undefined,
            'warning'
        );
    }

    private async applyPeppolChange(change: () => Promise<boolean>) {
        if (this.accountSaving || !this.canEditPeppolSettings) return;
        this.accountSaving = true;
        try {
            this.ea.publish('showOverlay', this.i18n.tr('overlay.saving'));
            if (await change()) {
                this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('alert.account.peppol-settings-updated')});
            }
        } finally {
            this.accountSaving = false;
            this.ea.publish('hideOverlay');
        }
    }

    private async savePeppolSettings(receiveDocuments: boolean): Promise<boolean> {
        if (!this.peppolSettings || !this.canEditPeppolSettings) return false;
        if (this.peppolSettings.receiveDocuments === receiveDocuments) return true;
        const request = this.registrationRequest;
        try {
            const settings = await this.companyService.updatePeppolSettings({receiveDocuments});
            if (request !== this.registrationRequest) return false;
            this.peppolSettings = {...settings};
            return true;
        } catch (error) {
            if (request !== this.registrationRequest) return false;
            // Reload the receiving flag when a provider write has an uncertain outcome.
            await this.refreshPeppolRegistration();
            const key = error instanceof Response && error.status === 409
                ? 'alert.account.receiving-conflict' : 'alert.account.peppol-settings-failed';
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr(key)});
            return false;
        }
    }

    get isVatExempt(): boolean {
        return this.company?.vatRuleset === 'VAT_EXEMPT_ART_56BIS';
    }

    get showVatDisplaySettings(): boolean {
        return hasVatNumber(this.company?.vatNumber);
    }

    get vatMode(): VatDisplayMode {
        return getVatDisplayMode(this.company?.vatNumber, this.vatDisplay.mode);
    }

    set vatMode(mode: VatDisplayMode) {
        if (!this.showVatDisplaySettings) {
            this.vatDisplay.setMode('incl');
            return;
        }
        this.vatDisplay.setMode(mode);
    }

    setVatRuleset(isExempt: boolean) {
        this.company.vatRuleset = isExempt ? 'VAT_EXEMPT_ART_56BIS' : 'VAT_REGISTERED';
    }

    attaching() {
        void this.refreshCurrentAccount();
        const st = (history.state ?? {}) as { runRegister?: boolean };
        if (st.runRegister) {
            history.replaceState({ ...st, runRegister: false }, '');
        }
        this.getCompany().then(() => {
            if (st.runRegister) this.register();
        }).catch(() => {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.account.load-failed')});
        });
        this.sub = this.ea.subscribe('account:register', () => {
            this.register();
        });
    }

    unbinding() {
        this.sub?.dispose();
        ++this.accountRequest;
        ++this.registrationRequest;
    }

    private async refreshCurrentAccount() {
        const request = ++this.accountRequest;
        this.currentAccount = undefined;
        try {
            const account = await this.accountService.getCurrentAccount();
            if (request === this.accountRequest) this.currentAccount = account;
        } catch {
            if (request === this.accountRequest) {
                this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.account.load-failed')});
            }
        }
    }

    async getCompany() {
        let company = this.companyService.myCompany;
        if (!company) {
            company = await this.companyService.getAndSetMyCompanyForToken();
        }
        this.company = JSON.parse(JSON.stringify(company));
        this.initialVatDisplayMode = this.vatDisplay.mode;

        void this.refreshPeppolRegistration();
    }

    async saveAccount() {
        if (this.accountSaving) return;
        this.accountSaving = true;
        try {
            this.ea.publish('showOverlay', this.i18n.tr('overlay.saving'));
            await this.companyService.updateCompany(this.company);
            this.initialVatDisplayMode = this.vatDisplay.mode;
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('alert.account.updated')});
        } catch(e) {
            console.error(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.account.update-failed')});
        } finally {
            this.accountSaving = false;
            this.ea.publish('hideOverlay');
        }
    }

    cancelChanges() {
        this.company = JSON.parse(JSON.stringify(this.companyService.myCompany));
        this.vatDisplay.setMode(this.initialVatDisplayMode);
        this.ea.publish('alert', {alertType: AlertType.Info, text: this.i18n.tr('alert.account.reverted')});
    }

    register() {
        this.confirmSending(true);
    }

    private async registerOnPeppol(): Promise<boolean> {
        try {
            this.setPeppolActive(await this.registrationService.registerCompany());
            if (!this.company.peppolActive) throw new Error('Sending was not activated');
            await this.refreshPeppolRegistration();
            return true;
        } catch (error: unknown) {
            if (!(error instanceof Response)) {
                this.ea.publish('alert', { alertType: AlertType.Danger, text: this.i18n.tr('alert.account.peppol-activation-request-failed') });
                return false;
            }
            const status = error.status;
            const body = await error.text().catch(() => "");
            switch (status) {
                case 403:
                    this.warningKey = 'account.registration-failed.contact-us';
                    break;
                case 409:
                    this.warningKey = 'account.registration-failed.contact-provider';
                    this.alreadyRegisteredProvider = body;
                    break;
                case 503:
                    this.warningKey = 'account.registration-failed.try-again-one-hour';
                    break;
                case 424:
                case 500:
                default:
                    this.warningKey = 'account.registration-failed.try-again-one-day';
                    break;
            }
            this.ea.publish('alert', { alertType: AlertType.Danger, text: this.i18n.tr('alert.account.peppol-activation-failed') });
            return false;
        }
    }

    private async unregisterFromPeppol(): Promise<boolean> {
        try {
            this.setPeppolActive(await this.registrationService.unregisterCompany());
            if (this.company.peppolActive) throw new Error('Sending was not removed');
            await this.refreshPeppolRegistration();
            return true;
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.account.peppol-remove-failed')});
            return false;
        }
    }

    private setPeppolActive(peppolActive: boolean) {
        this.company.peppolActive = peppolActive;
        if (this.companyService.myCompany) {
            this.companyService.myCompany.peppolActive = peppolActive;
        }
        localStorage.setItem('peppolActive', String(peppolActive));
        this.ea.publish('account:peppol-status-changed', peppolActive);
    }

    showChangePasswordModal() {
        this.changePasswordModal.showChangePasswordModal();
    }

    async downloadContract() {
        try {
            const blob = await this.registrationService.downloadSignedContract().then( res => res.blob() )
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = "signed_contract.pdf";
            document.body.appendChild(a);
            a.click();
            a.remove();
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.account.contract-download-failed')});
        }
    }

    validateEmailNotificationCcList(direction: 'incoming' | 'outgoing') {
        const property = direction === 'incoming'
            ? 'emailNotificationCcListIncoming'
            : 'emailNotificationCcListOutgoing';
        const ccList = this.company[property];
        if (ccList) {
            const validEmails = [];
            const emails = ccList.replace('\n', '').replace(' ', '').split(',');
            for (const email of emails) {
                if (validateEmail(email)) {
                    validEmails.push(email);
                }
            }
            if (validEmails.length) {
                this.company[property] = validEmails.join(',');
            } else {
                this.company[property] = undefined;
            }
        }
    }
}

import {CompanyDto, CompanyService} from "../services/app/company-service";
import {resolve} from "@aurelia/kernel";
import {AlertType} from "../components/alert/alert";
import {IEventAggregator, IDisposable} from "aurelia";
import {RegistrationService} from "../services/kyc/registration-service";
import {PeppolDirService} from "../services/peppol/peppol-dir-service";
import {ChangePasswordModal} from "./change-password-modal";
import {ConfirmationModalContext} from "../components/confirmation/confirmation-modal-context";
import {validateEmail} from "../app/util/email-validation";

export class Account {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private sub?: IDisposable;
    private readonly companyService = resolve(CompanyService);
    private readonly registrationService = resolve(RegistrationService);
    private readonly confirmationModalContext = resolve(ConfirmationModalContext);
    private readonly peppolDirService = resolve(PeppolDirService);
    private company: CompanyDto;
    public static PAYMENT_TERMS = ['15_DAYS', '30_DAYS', '60_DAYS', 'END_OF_NEXT_MONTH'];
    private alreadyPeppolActivated = false;
    changePasswordModal: ChangePasswordModal;
    private warningKey;
    private alreadyRegisteredProvider = '';

    attaching() {
        this.getCompany().catch(() => {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to get account"});
        });
        this.sub = this.ea.subscribe('account:register', () => {
            this.register();
        });
        const st = (history.state ?? {}) as any;
        if (st.runRegister) {
            history.replaceState({ ...st, runRegister: false }, '');// consume it so refresh doesn't re-run
            this.register();
        }
    }

    unbinding() {
        this.sub?.dispose();
    }

    async getCompany() {
        let company = this.companyService.myCompany;
        if (!company) {
            company = await this.companyService.getAndSetMyCompanyForToken();
        }
        this.company = JSON.parse(JSON.stringify(company));

        if (!this.company.peppolActive && this.company.peppolId) {
            const peppolDirectoryResponse = await this.peppolDirService.findByParticipant(this.company.peppolId); // TODO : peppolId undefined ?
            if (peppolDirectoryResponse.matches.length > 0) { //TODO : why not peppolDirectoryResponse.total-result-count ?
                this.alreadyPeppolActivated = true;
            }
        }
    }

    async saveAccount() {
        try {
            this.ea.publish('showOverlay', "Saving...");
            await this.companyService.updateCompany(this.company);
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Account updated successfully"});
        } catch(e) {
            console.error(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to update account"});
        } finally {
            this.ea.publish('hideOverlay');
        }
    }

    cancelChanges() {
        this.company = JSON.parse(JSON.stringify(this.companyService.myCompany));
        this.ea.publish('alert', {alertType: AlertType.Info, text: "Account changes reverted"});
    }

    async register() {
        const peppolDirectoryResponse = await this.peppolDirService.findByParticipant(this.company.peppolId);
        if (peppolDirectoryResponse.matches.length > 0) { //TODO : why not peppolDirectoryResponse.total-result-count ?
            this.confirmationModalContext.showConfirmationModal(
                "Activate on Peppol",
                "It looks like you are currently registered to the Peppol network via another Access Point.\n" +
                "Are you sure you wish to subscribe yourself to the Peppol network via Let's Peppol?\n" +
                "It might fail if you are still registered at the other Access Point provider.",
                () => this.registerOnPeppol(),
                undefined
            );
        } else {
            this.confirmationModalContext.showConfirmationModal(
                "Activate on Peppol",
                "Are you sure you wish to subscribe yourself to the Peppol network via Let's Peppol?\n" +
                "Make sure you are not subscribed via another service.",
                () => this.registerOnPeppol(),
                undefined
            );
        }
    }

    async registerOnPeppol() {
        try {
            this.company.peppolActive = await this.registrationService.registerCompany();
            localStorage.setItem('peppolActive', this.company.peppolActive);
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Activated company on Peppol"});
            window.location.reload();
        } catch (response: Response) {
            if (!response) {
                this.ea.publish('alert', { alertType: AlertType.Danger, text: "Failed to send activation request" });
                return;
            }
            const status = response.status;
            const body = await response.text().catch(() => "");
            switch (status) {
                case 403:
                    console.log("Forbidden: " + body);
                    this.warningKey = 'account.registration-failed.contact-us';
                    break;
                case 409:
                    console.log("Already registered at " + body);
                    this.warningKey = 'account.registration-failed.contact-provider';
                    this.alreadyRegisteredProvider = body;
                    break;
                case 503:
                    console.log("Service could not process request at this moment, try again later.");
                    this.warningKey = 'account.registration-failed.try-again-one-hour';
                    break;
                case 424:
                case 500:
                default:
                    console.log("Service could not process request");
                    this.warningKey = 'account.registration-failed.try-again-one-day';
                    break;
            }
            this.ea.publish('alert', { alertType: AlertType.Danger, text: "Failed to activate company on Peppol" });
        }
    }

    unregister() {
        this.confirmationModalContext.showConfirmationModal(
            "Remove From Peppol",
            "Are you sure you wish to unsubscribe yourself from the Peppol network?\n" +
            "Your invoices and credit notes will still be available.",
            () => this.unregisterFromPeppol(),
            undefined
        );
    }

    async unregisterFromPeppol() {
        try {
            this.company.peppolActive = await this.registrationService.unregisterCompany()
            localStorage.setItem('peppolActive', this.company.peppolActive);
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Removed company from Peppol"});
            window.location.reload();
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to remove company from Peppol"});
        }
    }

    showChangePasswordModal() {
        this.changePasswordModal.showChangePasswordModal();
    }

    getPaymentTerms() {
        return Account.PAYMENT_TERMS;
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
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to download signed contract"});
        }
    }

    validateEmailNotificationCCList() {
        if (this.company.emailNotificationCCList) {
            const validEmails = [];
            const emails = this.company.emailNotificationCCList.replace('\n', '').split(',');
            for (const email of emails) {
                if (validateEmail(email)) {
                    validEmails.push(email);
                }
            }
            if (validEmails.length) {
                this.company.emailNotificationCCList = validEmails.join(',');
            } else {
                this.company.emailNotificationCCList = undefined;
            }
        }
    }
}

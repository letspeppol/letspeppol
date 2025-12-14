import {CompanyDto, CompanyService} from "../services/app/company-service";
import {resolve} from "@aurelia/kernel";
import {AlertType} from "../components/alert/alert";
import {IEventAggregator, IDisposable} from "aurelia";
import {RegistrationService} from "../services/kyc/registration-service";
import {ChangePasswordModal} from "./change-password-modal";
import {ConfirmationModalContext} from "../components/confirmation/confirmation-modal-context";

export class Account {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private sub?: IDisposable;
    private readonly companyService = resolve(CompanyService);
    private readonly registrationService = resolve(RegistrationService);
    private readonly confirmationModalContext = resolve(ConfirmationModalContext);
    private company: CompanyDto;
    public static PAYMENT_TERMS = ['15_DAYS', '30_DAYS', '60_DAYS', 'END_OF_NEXT_MONTH'];
    changePasswordModal: ChangePasswordModal;

    attached() {
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
            company = await this.companyService.getAndSetMyCompanyForToken().then(result => this.company = result); //TODO : why company = ?
        }
        this.company = JSON.parse(JSON.stringify(company));
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

    discardChanges() {
        this.company = JSON.parse(JSON.stringify(this.companyService.myCompany));
        this.ea.publish('alert', {alertType: AlertType.Info, text: "Account changes reverted"});
    }

    register() {
        this.confirmationModalContext.showConfirmationModal(
            "Activate on Peppol",
            "Are you sure you wish to subscribe yourself to the Peppol network via Let's Peppol?\n" +
            "Make sure you are not subscribed via another service.",
            () => this.registerOnPeppol(),
            undefined
        );
    }

    async registerOnPeppol() {
        try {
            this.company.peppolActive = await this.registrationService.registerCompany()
            localStorage.setItem('peppolActive', this.company.peppolActive);
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Activated company on Peppol"});
            window.location.reload();
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to activate company on Peppol"});
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
}

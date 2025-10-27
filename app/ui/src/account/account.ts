import {CompanyDto, CompanyService} from "../services/app/company-service";
import {resolve} from "@aurelia/kernel";
import {AlertType} from "../components/alert/alert";
import {IEventAggregator} from "aurelia";
import {RegistrationService} from "../services/kyc/registration-service";
import {ChangePasswordModal} from "./change-password-modal";
import {ConfirmationModalContext} from "../components/confirmation/confirmation-modal-context";

export class Account {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
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
    }

    async getCompany() {
        let company = this.companyService.myCompany;
        if (!company) {
            company = await this.companyService.getAndSetMyCompanyForToken().then(result => this.company = result);
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

    unregister() {
        this.confirmationModalContext.showConfirmationModal(
            "Remove From Peppol",
            "Are you sure you whish to unsubscribe yourself from the Peppol network?\n" +
            "Your invoices and credit notes will still be available.",
            () => this.unregisterFromPeppol(),
            undefined
        );
    }

    async unregisterFromPeppol() {
        try {
            await this.registrationService.unregisterCompany()
            this.company.registeredOnPeppol = false;
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Removed company from Peppol"});
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

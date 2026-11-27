import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {I18N} from "@aurelia/i18n";
import {AlertType} from "../components/alert/alert";
import {AccountService, CurrentAccount} from "../services/kyc/account-service";
import {CompanyService} from "../services/app/company-service";
import {LoginService} from "../services/app/login-service";
import {OwnershipService} from "../services/app/ownership-service";
import {getVatDisplayMode, hasVatNumber, IVatDisplay, VatDisplayMode} from "../services/app/vat-display-service";
import {ChangePasswordModal} from "./change-password-modal";

export class Account {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);
    private readonly accountService = resolve(AccountService);
    private readonly companyService = resolve(CompanyService);
    private readonly loginService = resolve(LoginService);
    private readonly vatDisplay = resolve(IVatDisplay);
    readonly ownershipService = resolve(OwnershipService);
    changePasswordModal: ChangePasswordModal;
    currentAccount?: CurrentAccount;
    vatNumber?: string;
    private accountRequest = 0;

    attaching() {
        void this.refreshCurrentAccount();
        void this.loadVatNumber();
    }

    unbinding() {
        ++this.accountRequest;
    }

    get addCompanyPath(): string {
        return this.loginService.getCurrentOwnershipRoute('/add-ownership');
    }

    get showVatDisplaySettings(): boolean {
        return hasVatNumber(this.vatNumber);
    }

    get vatMode(): VatDisplayMode {
        return getVatDisplayMode(this.vatNumber, this.vatDisplay.mode);
    }

    set vatMode(mode: VatDisplayMode) {
        if (this.showVatDisplaySettings) {
            this.vatDisplay.setMode(mode);
        }
    }

    showChangePasswordModal() {
        this.changePasswordModal.showChangePasswordModal();
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

    private async loadVatNumber() {
        this.vatNumber = undefined;
        if (!this.ownershipService.hasCompany) {
            return;
        }
        try {
            const company = this.companyService.myCompany ?? await this.companyService.getAndSetMyCompanyForToken();
            this.vatNumber = company.vatNumber;
        } catch {
            this.vatNumber = undefined;
        }
    }
}

import {bindable, IEventAggregator} from "aurelia";
import {onModalEnter} from "../../../../components/util/modal-keyboard";
import {PaymentMeans} from "../../../../services/peppol/ubl";
import {InvoiceComposer} from "../../../invoice-composer";
import {resolve} from "@aurelia/kernel";
import {CompanyService} from "../../../../services/app/company-service";
import {AlertType} from "../../../../components/alert/alert";
import {I18N} from "@aurelia/i18n";

export class InvoicePaymentModal {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    invoiceComposer = resolve(InvoiceComposer);
    companyService = resolve(CompanyService);
    private readonly i18n = resolve(I18N);
    @bindable invoiceContext;
    paymentMeansCode;
    open = false;
    paymentMeans: PaymentMeans | undefined;

    showModal() {
        this.paymentMeansCode = structuredClone(this.invoiceContext.selectedInvoice.PaymentMeans?.PaymentMeansCode.value);
        this.paymentMeans = structuredClone(this.invoiceContext.selectedInvoice.PaymentMeans);
        this.ensureFinancialInstitutionBranch();
        this.open = true;
    }

    closeModal() {
        this.open = false;
    }

    savePaymentMeans() {
        if (this.paymentMeansCode === 30 && !this.paymentMeans?.PayeeFinancialAccount?.ID) {
            return;
        }
        this.normalizePaymentMeans();
        const paymentMeans = structuredClone(this.paymentMeans);
        this.open = false;
        this.invoiceContext.selectedInvoice.PaymentMeans = paymentMeans;
        this.ensureFinancialInstitutionBranch();
    }

    paymentMeansCodeChanged() {
        if (!this.paymentMeansCode) {
            this.paymentMeans = null;
            return;
        }
        if (this.paymentMeansCode === 30) {
            this.paymentMeans = this.invoiceComposer.getPaymentMeansForMyCompany(this.paymentMeansCode);
            this.ensureFinancialInstitutionBranch();
        } else {
            this.paymentMeans = {
                PaymentMeansCode: this.invoiceComposer.getPaymentMeansCode(this.paymentMeansCode)
            };
        }
    }

    private ensureFinancialInstitutionBranch() {
        if (this.paymentMeansCode !== 30 || !this.paymentMeans) {
            return;
        }
        if (!this.paymentMeans.PayeeFinancialAccount) {
            this.paymentMeans.PayeeFinancialAccount = {};
        }
        if (!this.paymentMeans.PayeeFinancialAccount.FinancialInstitutionBranch) {
            this.paymentMeans.PayeeFinancialAccount.FinancialInstitutionBranch = {};
        }
    }

    private normalizePaymentMeans() {
        if (this.paymentMeansCode !== 30 || !this.paymentMeans?.PayeeFinancialAccount) {
            return;
        }

        const account = this.paymentMeans.PayeeFinancialAccount;
        if (account.ID) {
            account.ID = account.ID.toUpperCase();
        }

        const bic = account.FinancialInstitutionBranch?.ID?.trim().toUpperCase();
        if (bic) {
            account.FinancialInstitutionBranch = {ID: bic};
        } else {
            delete account.FinancialInstitutionBranch;
        }
    }

    async saveIbanToAccount() {
        try {
            const account = this.paymentMeans.PayeeFinancialAccount;
            this.companyService.myCompany.iban = account.ID.toUpperCase();
            const bic = account.FinancialInstitutionBranch?.ID?.trim().toUpperCase();
            if (bic) {
                this.companyService.myCompany.bic = bic;
                if (!account.FinancialInstitutionBranch) {
                    account.FinancialInstitutionBranch = {};
                }
                account.FinancialInstitutionBranch.ID = bic;
            }
            await this.companyService.updateCompany(this.companyService.myCompany);
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('alert.iban.saved')});
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.company.save-failed')});
        }
    }

    onKeyDown(event: KeyboardEvent) {
        onModalEnter(event, () => this.savePaymentMeans());
    }
}

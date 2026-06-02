import {bindable, IEventAggregator} from "aurelia";
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
        console.log(this.paymentMeans);
        this.open = true;
    }

    closeModal() {
        this.open = false;
    }

    savePaymentMeans() {
        this.open = false;
        this.invoiceContext.selectedInvoice.PaymentMeans = this.paymentMeans;
    }

    paymentMeansCodeChanged() {
        if (!this.paymentMeansCode) {
            this.paymentMeans = null;
            return;
        }
        if (this.paymentMeansCode === 30) {
            this.paymentMeans = this.invoiceComposer.getPaymentMeansForMyCompany(this.paymentMeansCode);
        } else {
            this.paymentMeans = {
                PaymentMeansCode: this.invoiceComposer.getPaymentMeansCode(this.paymentMeansCode)
            };
        }
    }

    async saveIbanToAccount() {
        try {
            this.companyService.myCompany.iban = this.paymentMeans.PayeeFinancialAccount.ID.toUpperCase();
            await this.companyService.updateCompany(this.companyService.myCompany);
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('alert.iban.saved')});
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.company.save-failed')});
        }
    }
}

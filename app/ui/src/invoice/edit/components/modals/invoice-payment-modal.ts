import {bindable} from "aurelia";
import {PaymentMeans} from "../../../../services/peppol/ubl";
import {InvoiceComposer} from "../../../invoice-composer";
import {resolve} from "@aurelia/kernel";
import {CompanyService} from "../../../../services/app/company-service";

export class InvoicePaymentModal {
    invoiceComposer = resolve(InvoiceComposer);
    companyService = resolve(CompanyService);
    @bindable invoiceContext;
    paymentMeansCode;
    open = false;
    paymentMeans: PaymentMeans | undefined;

    showModal() {
        this.paymentMeansCode = structuredClone(this.invoiceContext.selectedInvoice.PaymentMeans.PaymentMeansCode.value);
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
}

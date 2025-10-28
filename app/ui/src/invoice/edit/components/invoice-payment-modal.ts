import {bindable} from "aurelia";
import {PaymentMeans} from "../../../services/peppol/ubl";
import {InvoiceComposer} from "../../invoice-composer";
import {resolve} from "@aurelia/kernel";

export class InvoicePaymentModal {
    invoiceComposer = resolve(InvoiceComposer);
    @bindable invoiceContext;
    @bindable selectedPaymentMeansCode;
    paymentMeansCode;
    open = false;
    paymentMeans: PaymentMeans | undefined;

    showModal() {
        this.paymentMeansCode = this.selectedPaymentMeansCode;
        this.paymentMeans = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.PaymentMeans));
        this.open = true;
    }

    closeModal() {
        this.open = false;
    }

    savePaymentMeans() {
        this.open = false;
        this.selectedPaymentMeansCode = this.paymentMeansCode;
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

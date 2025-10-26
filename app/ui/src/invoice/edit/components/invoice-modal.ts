import {bindable} from "aurelia";

export class InvoiceModal {
    @bindable invoiceContext;
    open = false;
    paymentTerms: string;
    issueDate: string;
    dueDate: string;

    showModal() {
        this.paymentTerms = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.PaymentTerms));
        this.open = true;
    }

    closeModal() {
        this.open = false;
    }

    saveDate() {
        this.open = false;
        this.invoiceContext.selectedInvoice.PaymentMeans = this.paymentMeans;
    }

    paymentMeansCodeChanged() {
        if (!this.selectedPaymentMeansCode) {
            this.paymentMeans = null;
            return;
        }
        console.log(this.selectedPaymentMeansCode);
        const index = this.invoiceContext.paymentMeansCodes.find(item => item.value === this.selectedPaymentMeansCode);
        if (index >= 0) {
            this.paymentMeans.PaymentMeansCode = JSON.parse(JSON.stringify(this.invoiceContext.paymentMeansCodes[index]));
            console.log(this.paymentMeans.PaymentMeansCode.value);
            console.log(this.paymentMeans.PaymentMeansCode.__name);
        }
    }
}
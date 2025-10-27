import {bindable} from "aurelia";

export class InvoicePaymentModal {
    @bindable invoiceContext;
    @bindable selectedPaymentMeansCode;
    open = false;

    showModal() {
        this.open = true;
    }

    closeModal() {
        this.open = false;
    }
}
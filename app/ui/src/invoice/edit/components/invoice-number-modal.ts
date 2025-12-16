import {bindable} from "aurelia";

export class InvoiceNumberModal {
    @bindable invoiceContext;
    open = false;
    invoiceNumber: string = '';
    sendFunction = () => { }

    showModal(sendFunction: () => void) {
        this.sendFunction = sendFunction;
        this.invoiceNumber = this.invoiceContext.selectedInvoice.ID ?? this.invoiceContext.nextInvoiceReference;
        this.open = true;
    }

    closeModal() {
        this.open = false;
    }

    sendInvoice() {
        this.invoiceContext.selectedInvoice.ID = this.invoiceNumber;
        if (this.sendFunction) {
            this.sendFunction();
            this.open = false;
        }
    }
}

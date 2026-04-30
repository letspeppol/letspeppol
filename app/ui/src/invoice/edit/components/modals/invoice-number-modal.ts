import {bindable} from "aurelia";
import {DocumentType} from "../../../../services/app/invoice-service";

export class InvoiceNumberModal {
    @bindable invoiceContext;
    open = false;
    invoiceNumber: string = '';
    sendFunction = () => { }

    get isCreditNote(): boolean {
        return this.invoiceContext?.selectedDocumentType === DocumentType.CREDIT_NOTE;
    }

    showModal(sendFunction: () => void) {
        this.sendFunction = sendFunction;
        this.invoiceNumber = this.invoiceContext.nextReference;
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

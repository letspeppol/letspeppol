import {bindable} from "aurelia";
import {onModalEnter} from "../../../../components/util/modal-keyboard";
import {DocumentType} from "../../../../services/app/invoice-service";

export class InvoiceNumberModal {
    @bindable invoiceContext;
    open = false;
    invoiceNumber: string = '';
    sendFunction = () => { }

    get isCreditNote(): boolean {
        return this.invoiceContext?.selectedDocumentType === DocumentType.CREDIT_NOTE;
    }

    async showModal(sendFunction: () => void) {
        this.sendFunction = sendFunction;
        this.invoiceNumber = this.invoiceContext.nextReference;
        this.open = true;
        await this.invoiceContext.referenceRequest;
        if (this.open && !this.invoiceNumber) {
            this.invoiceNumber = this.invoiceContext.nextReference;
        }
    }

    closeModal() {
        this.open = false;
    }

    sendInvoice() {
        if (!this.invoiceNumber) {
            return;
        }
        this.invoiceContext.selectedInvoice.ID = this.invoiceNumber;
        if (this.sendFunction) {
            this.sendFunction();
            this.open = false;
        }
    }

    onKeyDown(event: KeyboardEvent) {
        onModalEnter(event, () => this.sendInvoice());
    }
}

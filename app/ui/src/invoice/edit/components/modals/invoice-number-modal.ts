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
        if (event.key !== 'Enter' || this.shouldIgnoreEnter(event.target)) {
            return;
        }
        event.preventDefault();
        this.sendInvoice();
    }

    private shouldIgnoreEnter(target: EventTarget | null) {
        const element = target as HTMLElement | null;
        if (!element) {
            return false;
        }
        return ['BUTTON', 'A', 'TEXTAREA'].includes(element.tagName);
    }
}

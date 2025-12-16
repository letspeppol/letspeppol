import {bindable} from "aurelia";
import {DocumentType} from "../../../services/app/invoice-service";
import {CreditNote, Invoice} from "../../../services/peppol/ubl";
import {resolve} from "@aurelia/kernel";
import {InvoiceComposer} from "../../invoice-composer";

export class InvoiceModal {
    private readonly invoiceComposer = resolve(InvoiceComposer);
    private documentTypes = Object.values(DocumentType) as string[];
    @bindable invoiceContext;
    @bindable originalDocumentType: DocumentType;
    open = false;
    id: string;
    selectedDocumentType: DocumentType;
    buyerReference: string;
    note: string;

    showModal() {
        this.selectedDocumentType = JSON.parse(JSON.stringify(this.originalDocumentType));
        this.id = undefined;
        this.buyerReference = "NA";
        this.note = undefined;
        if (this.invoiceContext.selectedInvoice.ID) {
            this.id = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.ID));
        }
        if (this.invoiceContext.selectedInvoice.BuyerReference) {
            this.buyerReference = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.BuyerReference));
        }
        if (this.invoiceContext.selectedInvoice.Note) {
            this.note = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.Note));
        }
        this.open = true;
        setTimeout(() => window.document.getElementById('docNumber')?.focus(), 50);
    }

    closeModal() {
        this.open = false;
    }

    saveInvoiceInfo() {
        if (!this.buyerReference) {
            return;
        }
        this.open = false;
        if (this.selectedDocumentType !== this.originalDocumentType) {
            this.originalDocumentType = this.selectedDocumentType;
            if (this.selectedDocumentType === DocumentType.INVOICE) {
                this.invoiceContext.selectedInvoice = this.invoiceComposer.creditNoteToInvoice(this.invoiceContext.selectedInvoice as unknown as CreditNote);
            } else {
                this.invoiceContext.selectedInvoice = this.invoiceComposer.invoiceToCreditNote(this.invoiceContext.selectedInvoice as Invoice);
            }
        }
        this.invoiceContext.selectedInvoice.ID = this.id;
        this.invoiceContext.selectedInvoice.BuyerReference = this.buyerReference;
        this.invoiceContext.selectedInvoice.Note = this.note;
        console.log(this.originalDocumentType);
    }

    onKeyDown(e: KeyboardEvent) {
        if (e.key === 'Enter') {
            this.saveInvoiceInfo();
        }
    }
}

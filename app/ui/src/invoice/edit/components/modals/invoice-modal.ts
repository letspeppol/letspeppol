import {bindable} from "aurelia";
import {DocumentDirection, DocumentDto, DocumentType, InvoiceService} from "../../../../services/app/invoice-service";
import {CreditNote, Invoice} from "../../../../services/peppol/ubl";
import {resolve} from "@aurelia/kernel";
import {InvoiceComposer} from "../../../invoice-composer";
import {I18N} from "@aurelia/i18n";

export class InvoiceModal {
    private readonly invoiceComposer = resolve(InvoiceComposer);
    private readonly invoiceService = resolve(InvoiceService);
    private readonly i18n = resolve(I18N);
    private documentTypes = Object.values(DocumentType) as string[];
    @bindable invoiceContext;
    @bindable originalDocumentType: DocumentType;
    open = false;
    id: string;
    selectedDocumentType: DocumentType;
    buyerReference: string;
    orderReference: string;
    note: string;
    referenceableInvoices: DocumentDto[] = [];
    selectedInvoiceReferenceId: string | undefined;

    get isCreditNote(): boolean {
        return this.selectedDocumentType === DocumentType.CREDIT_NOTE;
    }

    showModal() {
        this.selectedDocumentType = JSON.parse(JSON.stringify(this.originalDocumentType));
        this.id = undefined;
        if (this.invoiceContext.selectedInvoice.BuyerReference) {
            this.buyerReference = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.BuyerReference));
        }
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
        this.selectedInvoiceReferenceId = undefined;
        this.open = true;
        setTimeout(() => window.document.getElementById('docNumber')?.focus(), 50);
        const existingRef = this.invoiceContext.selectedInvoice?.BillingReference?.[0]?.InvoiceDocumentReference?.ID;
        this.loadReferenceableInvoices().then(() => {
            this.selectedInvoiceReferenceId = existingRef;
        });
    }

    closeModal() {
        this.open = false;
    }

    saveInvoiceInfo() {
        if (!this.buyerReference && !this.orderReference) {
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
        if (this.orderReference) {
            this.invoiceContext.selectedInvoice.OrderReference = {ID: this.orderReference};
        } else {
            this.invoiceContext.selectedInvoice.OrderReference = undefined;
        }
        if (this.isCreditNote) {
            const vatNote = this.i18n.tr('invoice.modal.credit-note-vat-note');
            if (!this.note || !this.note.trim()) {
                this.note = vatNote;
            }
        }
        this.invoiceContext.selectedInvoice.Note = this.note;
        if (this.isCreditNote) {
            this.applyBillingReference();
        } else {
            this.invoiceContext.selectedInvoice.BillingReference = undefined;
        }
    }

    private applyBillingReference() {
        if (!this.selectedInvoiceReferenceId) {
            this.invoiceContext.selectedInvoice.BillingReference = undefined;
            return;
        }
        const selected = this.referenceableInvoices.find(inv => inv.invoiceReference === this.selectedInvoiceReferenceId);
        const existingIssueDate = this.invoiceContext.selectedInvoice.BillingReference?.[0]?.InvoiceDocumentReference?.IssueDate;
        const issueDate = selected?.issueDate ? selected.issueDate.substring(0, 10) : existingIssueDate;
        this.invoiceContext.selectedInvoice.BillingReference = [{
            InvoiceDocumentReference: {
                ID: this.selectedInvoiceReferenceId,
                IssueDate: issueDate,
            }
        }];
    }

    private async loadReferenceableInvoices() {
        if (!this.isCreditNote) {
            this.referenceableInvoices = [];
            return;
        }
        try {
            const page = await this.invoiceService.getDocuments({
                type: DocumentType.INVOICE,
                direction: DocumentDirection.OUTGOING,
                partnerPeppolId: this.customerPeppolId(),
                pageable: {page: 0, size: 100, sort: [{property: 'issueDate', direction: 'desc'}]},
            });
            this.referenceableInvoices = page.content.filter(d => !!d.invoiceReference);
        } catch {
            this.referenceableInvoices = [];
        }
    }

    private customerPeppolId(): string | undefined {
        const endpoint = this.invoiceContext?.selectedInvoice?.AccountingCustomerParty?.Party?.EndpointID;
        const value = endpoint?.value?.trim();
        if (!value) return undefined;
        const scheme = endpoint?.__schemeID?.trim();
        return scheme ? `${scheme}:${value}` : value;
    }

    selectedDocumentTypeChanged() {
        this.loadReferenceableInvoices();
    }

    onKeyDown(e: KeyboardEvent) {
        if (e.key === 'Enter') {
            this.saveInvoiceInfo();
        }
    }
}

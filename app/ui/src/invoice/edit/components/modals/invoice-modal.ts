import {bindable, observable} from "aurelia";
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
    @observable selectedDocumentType: DocumentType = undefined;
    buyerReference: string;
    orderReference: string;
    note: string;
    referenceableInvoices: DocumentDto[] = [];
    selectedInvoiceReferenceId: string | undefined;
    private referenceLoadToken = 0;
    private opening = false;

    get isCreditNote(): boolean {
        return this.selectedDocumentType === DocumentType.CREDIT_NOTE;
    }

    get hasReference(): boolean {
        return !!this.buyerReference?.trim() || !!this.orderReference?.trim();
    }

    showModal() {
        this.opening = true;
        this.selectedDocumentType = JSON.parse(JSON.stringify(this.originalDocumentType));
        this.opening = false;
        this.id = undefined;
        this.buyerReference = undefined;
        this.orderReference = undefined;
        this.note = undefined;
        if (this.invoiceContext.selectedInvoice.ID) {
            this.id = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.ID));
        }
        if (this.invoiceContext.selectedInvoice.BuyerReference) {
            this.buyerReference = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.BuyerReference));
        }
        if (this.invoiceContext.selectedInvoice.OrderReference?.ID) {
            this.orderReference = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.OrderReference.ID));
        }
        if (this.invoiceContext.selectedInvoice.Note) {
            this.note = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.Note));
        }
        this.referenceableInvoices = [];
        this.selectedInvoiceReferenceId = undefined;
        this.open = true;
        setTimeout(() => window.document.getElementById('docNumber')?.focus(), 50);
        const existingRef = this.invoiceContext.selectedInvoice?.BillingReference?.[0]?.InvoiceDocumentReference?.ID;
        const token = ++this.referenceLoadToken;
        this.loadReferenceableInvoices(token).then(() => {
            if (token === this.referenceLoadToken) {
                this.selectedInvoiceReferenceId = existingRef;
            }
        });
    }

    closeModal() {
        this.open = false;
    }

    saveInvoiceInfo() {
        if (!this.hasReference) {
            return;
        }
        const buyerReference = this.buyerReference?.trim();
        const orderReference = this.orderReference?.trim();
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
        this.invoiceContext.selectedInvoice.BuyerReference = buyerReference || undefined;
        this.invoiceContext.selectedInvoice.OrderReference = orderReference ? {ID: orderReference} : undefined;
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

    private async loadReferenceableInvoices(token: number) {
        if (!this.isCreditNote) {
            if (token === this.referenceLoadToken) {
                this.referenceableInvoices = [];
            }
            return;
        }
        try {
            const page = await this.invoiceService.getDocuments({
                type: DocumentType.INVOICE,
                direction: DocumentDirection.OUTGOING,
                partnerPeppolId: this.customerPeppolId(),
                draft: false,
                pageable: {page: 0, size: 100, sort: [{property: 'issueDate', direction: 'desc'}]},
            });
            if (token !== this.referenceLoadToken) {
                return;
            }
            this.referenceableInvoices = this.selectableInvoices(page.content);
        } catch {
            if (token === this.referenceLoadToken) {
                this.referenceableInvoices = [];
            }
        }
    }

    private selectableInvoices(documents: DocumentDto[]): DocumentDto[] {
        const byInvoiceReference = new Map<string, DocumentDto>();
        for (const document of documents) {
            if (!document.invoiceReference || !document.processedOn || document.processedStatus) {
                continue;
            }
            if (!byInvoiceReference.has(document.invoiceReference)) {
                byInvoiceReference.set(document.invoiceReference, document);
            }
        }
        const referenced = this.invoiceContext.selectedInvoice?.BillingReference?.[0]?.InvoiceDocumentReference;
        if (referenced?.ID && !byInvoiceReference.has(referenced.ID)) {
            byInvoiceReference.set(referenced.ID, {
                invoiceReference: referenced.ID,
                issueDate: referenced.IssueDate,
            } as DocumentDto);
        }
        return [...byInvoiceReference.values()];
    }

    private customerPeppolId(): string | undefined {
        const endpoint = this.invoiceContext?.selectedInvoice?.AccountingCustomerParty?.Party?.EndpointID;
        const value = endpoint?.value?.trim();
        if (!value) return undefined;
        const scheme = endpoint?.__schemeID?.trim();
        return scheme ? `${scheme}:${value}` : value;
    }

    selectedDocumentTypeChanged() {
        if (this.opening) {
            return;
        }
        void this.loadReferenceableInvoices(++this.referenceLoadToken);
    }

    onKeyDown(e: KeyboardEvent) {
        if (e.key === 'Enter') {
            this.saveInvoiceInfo();
        }
    }
}

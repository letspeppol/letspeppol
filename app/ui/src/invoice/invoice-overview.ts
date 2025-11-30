import {resolve} from "@aurelia/kernel";
import {DocumentType, InvoiceContext} from "./invoice-context";
import {parseCreditNote, parseInvoice} from "../services/peppol/ubl-parser";
import {AlertType} from "../components/alert/alert";
import {IEventAggregator, watch} from "aurelia";
import {DocumentQuery, ProxyService} from "../services/proxy/proxy-service";
import {InvoiceDraftDto, DocumentDto, InvoiceService} from "../services/app/invoice-service";
import moment from "moment";

export class InvoiceOverview {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private proxyService = resolve(ProxyService);
    private invoiceService = resolve(InvoiceService);
    private invoiceContext = resolve(InvoiceContext);
    invoices: DocumentDto[] | InvoiceDraftDto[] = [];
    box = 'all'
    query: DocumentQuery = {page: 1};

    attached() {
        this.loadInvoices();
        this.loadDrafts();
        this.invoiceContext.initCompany();
    }

    async loadDrafts() {
        this.invoiceContext.drafts = await this.invoiceService.getInvoiceDrafts();
    }

    @watch((vm) => [vm.query.docId, vm.query.counterPartyNameLike])
    loadInvoices() {
        if (this.box === 'drafts') {
            this.invoices = this.invoiceContext.drafts.filter(i => {
               return (!this.query.docType || i.docType === this.query.docType) &&
                (!this.query.counterPartyNameLike || !i.counterPartyName || i.counterPartyName.toLowerCase().includes(this.query.counterPartyNameLike.toLowerCase())) &&
                (!this.query.docId || !i.docId || i.docId.toLowerCase() === this.query.docId.toLowerCase())
                ;
            });
        } else {
            this.invoiceService.getDocuments(this.query).then(items => this.invoices = items);
        }
    }

    changeDocType(value: undefined | "invoice" | "credit-note") {
        this.query.docType = value;
        this.loadInvoices();
    }

    setActiveItems(box) {
        this.box = box;
        switch (box) {
            case 'all':
                this.query.direction = undefined;
                this.loadInvoices();
                break;
            case 'outgoing':
            case 'incoming':
                this.query.direction = box;
                this.loadInvoices();
                break;
            case 'drafts':
                this.invoices = this.invoiceContext.drafts;
                break;
        }
    }

    newInvoice() {
        this.ea.publish('newInvoice');
    }

    newCreditNote() {
        this.ea.publish('newCreditNote');
    }

    selectItem(item: DocumentDto | InvoiceDraftDto) {
        this.invoiceContext.selectedDraft = undefined;
        if (this.box === 'drafts') {
            const doc = item as InvoiceDraftDto;
            this.invoiceContext.readOnly = false;
            this.invoiceContext.selectedDocumentType = item.docType as DocumentType;
            if (item.docType === DocumentType.CreditNote) {
                this.invoiceContext.selectedInvoice = parseCreditNote(doc.xml);
            } else {
                this.invoiceContext.selectedInvoice = parseInvoice(doc.xml);
            }
            this.invoiceContext.selectedDraft = doc;
            this.invoiceContext.selectedInvoiceXML = doc.xml;
        } else {
            const doc = item as DocumentDto;
            this.invoiceService.getDocument(doc.id).then((document) => {
                this.invoiceContext.readOnly = true;
                this.invoiceContext.selectedInvoice = parseInvoice(document.ubl);
                if (!this.invoiceContext.selectedInvoice) { // TODO test
                    this.invoiceContext.selectedDocumentType = DocumentType.CreditNote;
                    this.invoiceContext.selectedInvoice = parseCreditNote(document.ubl);
                } else {
                    this.invoiceContext.selectedDocumentType = DocumentType.Invoice;
                }
                this.invoiceContext.selectedInvoiceXML = document.ubl;
            });
        }
    }

    nextPage() {
        if (this.query.page > 1 && this.invoices.length === 0) {
            return;
        }
        this.query.page++;
        this.loadInvoices();
    }

    previousPage() {
        if (this.query.page === 1) {
            return;
        }
        this.query.page--;
        this.loadInvoices();
    }

    isOverdue(item: DocumentDto) {
        if (!item.dueDate) {
            return false;
        }
        return moment().isAfter(moment(item.dueDate));
    }

    async deleteDraft(event: Event, draft: InvoiceDraftDto) {
        event.stopPropagation();
        try {
            await this.invoiceService.deleteInvoiceDraft(draft.id)
            this.invoiceContext.deleteDraft(draft);
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Draft deleted"});
        } catch (e) {
            console.log(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to delete draft"});
        }
        return false;
    }

    formatDate(date) {
        return moment(date).format('D/M/YYYY');
    }

    async markPaid(event: Event, item: DocumentDto) {
        event.stopPropagation();
        try {
            const datePaid = moment().toISOString();
            this.proxyService.markPaid(item.id, datePaid);
            item.paidOn = datePaid;
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Invoice marked as paid"});
        } catch (e) {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to mark invoice as paid"});
        }
    }

    async markUnpaid(event: Event, item: DocumentDto) {
        event.stopPropagation();
        try {
            this.proxyService.markPaid(item.id, null);
            item.paidOn = undefined;
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Invoice marked as unpaid"});
        } catch (e) {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to mark invoice as unpaid"});
        }
    }
}

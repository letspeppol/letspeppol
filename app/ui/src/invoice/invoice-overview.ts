import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "./invoice-context";
import {parseInvoice} from "../services/peppol/ubl-parser";
import {AlertType} from "../components/alert/alert";
import {IEventAggregator, watch} from "aurelia";
import {DocumentQuery, ListItem, ProxyService} from "../services/proxy/proxy-service";
import {InvoiceDraftDto, InvoiceService} from "../services/app/invoice-service";

export class InvoiceOverview {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private letsPeppolService = resolve(ProxyService);
    private invoiceService = resolve(InvoiceService);
    private invoiceContext = resolve(InvoiceContext);
    invoices: ListItem[] | InvoiceDraftDto[] = [];
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
            this.letsPeppolService.getDocuments(this.query).then(items => this.invoices = items);
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

    selectItem(item: ListItem | InvoiceDraftDto) {
        this.invoiceContext.selectedDraft = undefined;
        if (this.box === 'drafts') {
            const doc = item as InvoiceDraftDto;
            this.invoiceContext.selectedInvoice = parseInvoice(doc.xml);
            this.invoiceContext.selectedDraft = doc;
        } else {
            // const doc = item as ListItem;
            // this.letsPeppolService.getDocument(doc.type, doc.direction, doc.uuid).then((response) => {
            //     this.invoiceContext.selectedInvoice = parseInvoice(response);
            // });
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
}

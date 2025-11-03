import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "./invoice-context";
import {parseInvoice} from "../services/peppol/ubl-parser";
import {AlertType} from "../components/alert/alert";
import {IEventAggregator} from "aurelia";
import {ListItemV1, ProxyService} from "../services/proxy/proxy-service";
import {InvoiceDraftDto, InvoiceService} from "../services/app/invoice-service";

export class InvoiceOverview {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private letsPeppolService = resolve(ProxyService);
    private invoiceService = resolve(InvoiceService);
    private invoiceContext = resolve(InvoiceContext);
    all: ListItemV1[] = [];
    incoming: ListItemV1[] = [];
    outgoing: ListItemV1[] = [];
    activeItems: ListItemV1[] | InvoiceDraftDto[] = [];
    box = 'all'
    page = 1;

    attached() {
        this.loadInvoices();
        this.loadDrafts();
        this.invoiceContext.initCompany();
    }

    loadInvoices() {
        const ip = this.letsPeppolService.getIncomingInvoices(this.page).then(items => this.incoming = items);
        const op = this.letsPeppolService.getOutgoingInvoices(this.page).then(items => this.outgoing = items);
        Promise.all([ip, op]).then(([incoming, outgoing]) => {
            this.all = [...incoming, ...outgoing].sort((a, b) => Date.parse(b.requestSentAt) - Date.parse(a.requestSentAt));
            this.setActiveItems('all');
        }).catch(() => this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to get invoices"}));
    }

    async loadDrafts() {
        this.invoiceContext.drafts = await this.invoiceService.getInvoiceDrafts();
    }

    setActiveItems(box) {
        this.box = box;
        switch (box) {
            case 'all':
                this.activeItems = this.all;
                break;
            case 'outgoing':
                this.activeItems = this.outgoing;
                break;
            case 'incoming':
                this.activeItems = this.incoming;
                break;
            case 'drafts':
                this.activeItems = this.invoiceContext.drafts;
                break;
        }
    }

    newInvoice() {
        this.ea.publish('newInvoice');
    }

    newCreditNote() {
        this.ea.publish('newCreditNote');
    }

    selectItem(item: ListItemV1 | InvoiceDraftDto) {
        this.invoiceContext.selectedDraft = undefined;
        if (this.box === 'drafts') {
            const doc = item as InvoiceDraftDto;
            this.invoiceContext.selectedInvoice = parseInvoice(doc.xml);
            this.invoiceContext.selectedDraft = doc;
        } else {
            const doc = item as ListItemV1;
            this.letsPeppolService.getDocument(doc.type, doc.direction, doc.uuid).then((response) => {
                this.invoiceContext.selectedInvoice = parseInvoice(response);
            });
        }
    }

    nextPage() {
        if (this.page > 1 && this.all.length === 0) {
            return;
        }
        this.page++;
        this.loadInvoices();
    }

    previousPage() {
        if (this.page === 1) {
            return;
        }
        this.page--;
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

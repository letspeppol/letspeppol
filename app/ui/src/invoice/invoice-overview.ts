import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "./invoice-context";
import {AlertType} from "../components/alert/alert";
import {IEventAggregator, watch} from "aurelia";
import {
    DocumentType,
    DocumentDto,
    DocumentPageDto,
    DocumentQuery,
    InvoiceService, DocumentDirection,
} from "../services/app/invoice-service";
import moment from "moment";

export class InvoiceOverview {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private invoiceService = resolve(InvoiceService);
    private invoiceContext = resolve(InvoiceContext);
    invoicePage: DocumentPageDto = undefined;
    box = 'ALL'
    query: DocumentQuery = {pageable: {page: 0, size: 20}};

    attached() {
        this.loadInvoices();
        this.loadDrafts();
        this.invoiceContext.initCompany();
    }

    async loadDrafts() {
        this.invoiceContext.draftPage = await this.invoiceService.getDocuments({...this.query, draft: true });
    }

    @watch((vm) => [vm.query.invoiceReference, vm.query.partnerName])
    loadInvoices() {
        if (this.box === 'drafts') {
            this.invoiceService.getDocuments({...this.query, draft: true }).then(page => this.invoicePage = page);
            // this.invoices = this.invoiceContext.drafts.filter(i => {
            //    return (!this.query.type || i.docType === this.query.type) &&
            //     (!this.query.counterPartyNameLike || !i.counterPartyName || i.counterPartyName.toLowerCase().includes(this.query.counterPartyNameLike.toLowerCase())) &&
            //     (!this.query.docId || !i.docId || i.docId.toLowerCase() === this.query.docId.toLowerCase())
            //     ;
            // });
        } else {
            this.invoiceService.getDocuments({...this.query, draft: false}).then(page => this.invoicePage = page);
        }
    }

    changeDocType(value: DocumentType) {
        this.query.type = value;
        this.loadInvoices();
    }

    setActiveItems(box) {
        this.box = box;
        switch (box) {
            case 'ALL':
                this.query.direction = undefined;
                this.loadInvoices();
                break;
            case DocumentDirection.INCOMING:
            case DocumentDirection.OUTGOING:
                this.query.direction = box;
                this.loadInvoices();
                break;
            case 'DRAFTS':
                this.invoicePage = this.invoiceContext.draftPage;
                break;
        }
    }

    newInvoice() {
        this.ea.publish('newInvoice');
    }

    newCreditNote() {
        this.ea.publish('newCreditNote');
    }

    selectItem(item: DocumentDto) {
        history.pushState({}, '', `/invoices/${item.id}`);
        this.invoiceContext.selectInvoice(item);
    }

    nextPage() {
        this.query.pageable.page++;
        this.loadInvoices();
    }

    previousPage() {
        if (this.query.pageable.page === 1) {
            return;
        }
        this.query.pageable.page--;
        this.loadInvoices();
    }

    isOverdue(item: DocumentDto) {
        if (!item.dueDate) {
            return false;
        }
        return moment().isAfter(moment(item.dueDate));
    }

    async deleteDraft(event: Event, draft: DocumentDto) {
        event.stopPropagation();
        try {
            await this.invoiceService.deleteDocument(draft.id)
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
            await this.invoiceService.togglePaidDocument(item.id);
            item.paidOn = datePaid;
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Invoice marked as paid"});
        } catch (e) {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to mark invoice as paid"});
        }
    }
}

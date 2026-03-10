import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "./invoice-context";
import {AlertType} from "../components/alert/alert";
import {IDisposable, IEventAggregator, watch} from "aurelia";
import {
    DocumentType,
    DocumentDto,
    DocumentQuery,
    InvoiceService, DocumentDirection,
} from "../services/app/invoice-service";
import moment from "moment";
import {IRouter} from "@aurelia/router";

export class InvoiceOverview {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private invoiceService = resolve(InvoiceService);
    private invoiceContext = resolve(InvoiceContext);
    private router = resolve(IRouter);
    query: DocumentQuery = {pageable: {page: 0, size: 20}};
    private resetSubscription: IDisposable;

    attached() {
        this.invoiceContext.initCompany();
        this.resetSubscription = this.ea.subscribe('invoicesReset', () => this.setActiveItems(this.invoiceContext.activeBox));
        this.setActiveItems(this.invoiceContext.activeBox);
        this.loadDrafts();
    }

    detaching() {
        this.resetSubscription?.dispose();
    }

    async loadDrafts() {
        this.invoiceContext.draftPage = await this.invoiceService.getDocuments({...this.query, draft: true });
    }

    @watch((vm) => [vm.query.invoiceReference, vm.query.partnerName])
    loadInvoices() {
        this.invoiceService.getDocuments({
            ...this.query,
            draft: this.invoiceContext.activeBox === 'drafts'
        }).then(page => this.invoiceContext.invoicePage = page);
    }

    changeDocType(value: DocumentType) {
        this.query.type = value;
        this.loadInvoices();
    }

    setActiveItems(box) {
        this.invoiceContext.activeBox = box;
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
                this.invoiceContext.invoicePage = this.invoiceContext.draftPage;
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
        this.router.load(`/invoices/${item.id}`);
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
            if (item.paidOn) {
                item.paidOn = undefined;
                this.ea.publish('alert', {alertType: AlertType.Success, text: "Invoice marked as unpaid"});
            } else {
                item.paidOn = datePaid;
                this.ea.publish('alert', {alertType: AlertType.Success, text: "Invoice marked as paid"});
            }
        } catch (e) {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to change invoice paid status"});
        }
    }
}

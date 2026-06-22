import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../invoice-context";
import {AlertType} from "../../components/alert/alert";
import {bindable, IDisposable, IEventAggregator, watch} from "aurelia";
import {
    DocumentType,
    DocumentDto,
    DocumentQuery,
    InvoiceService, DocumentDirection,
} from "../../services/app/invoice-service";
import {IVatDisplay, VatDisplayMode} from "../../services/app/vat-display-service";
import moment from "moment";
import {IRouter} from "@aurelia/router";
import {UploadUblModal} from "./components/upload-ubl-modal";
import {I18N} from "@aurelia/i18n";

type SortDirection = "asc" | "desc";

export class InvoiceOverview {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private invoiceService = resolve(InvoiceService);
    private invoiceContext = resolve(InvoiceContext);
    private router = resolve(IRouter);
    private readonly i18n = resolve(I18N);
    private vatDisplay = resolve(IVatDisplay);
    vatMode: VatDisplayMode = this.vatDisplay.mode;
    query: DocumentQuery = {pageable: {page: 0, size: 20, sort: [{property: 'issueDate', direction: 'desc'}]}};
    activeSortProperty = 'issueDate';
    activeSortDirection: SortDirection = 'desc';
    private resetSubscription: IDisposable;
    private vatUnsubscribe: () => void;

    @bindable uploadUblModal: UploadUblModal;

    attached() {
        this.invoiceContext.initCompany();
        this.resetSubscription = this.ea.subscribe('invoicesReset', () => this.setActiveItems(this.invoiceContext.activeBox));
        this.vatUnsubscribe = this.vatDisplay.subscribe(mode => this.vatMode = mode);
        this.setActiveItems(this.invoiceContext.activeBox);
        this.loadDrafts();
    }

    detaching() {
        this.resetSubscription?.dispose();
        this.vatUnsubscribe?.();
    }

    async loadDrafts() {
        this.invoiceContext.draftPage = await this.invoiceService.getDocuments({...this.query, draft: true });
        if (this.invoiceContext.activeBox === 'DRAFTS') {
            this.invoiceContext.invoicePage = this.invoiceContext.draftPage;
        }
    }

    @watch((vm) => [vm.query.invoiceReference, vm.query.partnerName])
    queryChanged() {
        this.query.pageable.page = 0;
        this.reloadCurrentView();
    }

    loadInvoices() {
        this.invoiceService.getDocuments({
            ...this.query,
            draft: this.invoiceContext.activeBox === 'DRAFTS'
        }).then(page => this.invoiceContext.invoicePage = page);
    }

    changeDocType(value: DocumentType) {
        this.query.type = value;
        this.query.pageable.page = 0;
        this.reloadCurrentView();
    }

    setActiveItems(box) {
        this.invoiceContext.activeBox = box;
        switch (box) {
            case 'ALL':
                this.query.direction = undefined;
                this.query.pageable.page = 0;
                this.loadInvoices();
                break;
            case DocumentDirection.INCOMING:
            case DocumentDirection.OUTGOING:
                this.query.direction = box;
                this.query.pageable.page = 0;
                this.loadInvoices();
                break;
            case 'DRAFTS':
                this.query.pageable.page = 0;
                this.loadDrafts();
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
        if (this.query.pageable.page <= 0) {
            return;
        }
        this.query.pageable.page--;
        this.reloadCurrentView();
    }

    get pageStart() {
        const total = this.invoiceContext.invoicePage?.totalElements ?? 0;
        if (!total) {
            return 0;
        }
        return (this.invoiceContext.invoicePage.page * this.invoiceContext.invoicePage.size) + 1;
    }

    get pageEnd() {
        const total = this.invoiceContext.invoicePage?.totalElements ?? 0;
        if (!total) {
            return 0;
        }
        return Math.min((this.invoiceContext.invoicePage.page + 1) * this.invoiceContext.invoicePage.size, total);
    }

    toggleSort(property: string) {
        const currentDirection = this.sortDirection(property);
        const direction: SortDirection = currentDirection === 'asc' ? 'desc' : 'asc';
        this.query.pageable.sort = [{property, direction}];
        this.activeSortProperty = property;
        this.activeSortDirection = direction;
        this.query.pageable.page = 0;
        this.reloadCurrentView();
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
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('alert.invoice.draft-deleted')});
        } catch (e) {
            console.log(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.invoice.draft-delete-overview-failed')});
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
                this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr(`alert.invoice.marked-unpaid.${item.type}`)});
            } else {
                item.paidOn = datePaid;
                this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr(`alert.invoice.marked-paid.${item.type}`)});
            }
        } catch (e) {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.invoice.paid-status-failed')});
        }
    }

    showUploadUblModal() {
        this.uploadUblModal.showModal();
    }

    private reloadCurrentView() {
        if (this.invoiceContext.activeBox === 'DRAFTS') {
            this.loadDrafts();
            return;
        }
        this.loadInvoices();
    }

    private sortDirection(property: string): SortDirection | undefined {
        return this.query.pageable.sort?.find(sort => sort.property === property)?.direction;
    }
}

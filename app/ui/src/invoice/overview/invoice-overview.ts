import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../invoice-context";
import {AlertType} from "../../components/alert/alert";
import {bindable, IDisposable, IEventAggregator, watch} from "aurelia";
import {
    DocumentType,
    DocumentDto,
    DocumentQuery,
    InvoiceService, DocumentDirection,
    baseDocumentType, isInvoiceType, isSelfBillingType,
} from "../../services/app/invoice-service";
import {getVatDisplayMode, IVatDisplay, VatDisplayMode} from "../../services/app/vat-display-service";
import moment from "moment";
import {IRouter} from "@aurelia/router";
import {UploadUblModal} from "./components/upload-ubl-modal";
import {I18N} from "@aurelia/i18n";
import {CompanyService} from "../../services/app/company-service";
import {currentOwnershipRoute} from "../../services/app/ownership-route";
import {parseCreditNote, parseInvoice} from "../../services/peppol/ubl-parser";
import {OwnershipService} from "../../services/app/ownership-service";

type SortDirection = "asc" | "desc";
type StatusFilter = "ALL" | "UNPAID" | "OVERDUE" | "PAID" | "ERROR";

export class InvoiceOverview {
    readonly isInvoiceType = isInvoiceType;
    readonly isSelfBillingType = isSelfBillingType;
    readonly baseDocumentType = baseDocumentType;
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private invoiceService = resolve(InvoiceService);
    private invoiceContext = resolve(InvoiceContext);
    private router = resolve(IRouter);
    private readonly i18n = resolve(I18N);
    private readonly companyService = resolve(CompanyService);
    private readonly ownershipService = resolve(OwnershipService);
    private readonly vatDisplay = resolve(IVatDisplay);
    vatMode: VatDisplayMode = getVatDisplayMode(this.companyService.myCompany?.vatNumber, this.vatDisplay.mode);
    query: DocumentQuery = {pageable: {page: 0, size: 20, sort: [{property: 'issueDate', direction: 'desc'}]}}; 
    activeSortProperty = 'issueDate';
    activeSortDirection: SortDirection = 'desc';
    statusFilter: StatusFilter = 'ALL';
    private resetSubscription: IDisposable;
    private unsubscribeVatDisplay?: () => void;
    private pendingLoads = 0;

    @bindable uploadUblModal: UploadUblModal;

    async attached() {
        await this.invoiceContext.initCompany();
        this.updateVatDisplayMode();
        this.unsubscribeVatDisplay = this.vatDisplay.subscribe(mode => this.updateVatDisplayMode(mode));
        this.resetSubscription = this.ea.subscribe('invoicesReset', () => this.setActiveItems(this.invoiceContext.activeBox));
        this.setActiveItems(this.invoiceContext.activeBox);
        this.loadDrafts();
    }

    detaching() {
        this.resetSubscription?.dispose();
        this.unsubscribeVatDisplay?.();
    }

    async loadDrafts() {
        const showsDrafts = this.invoiceContext.activeBox === 'DRAFTS';
        this.invoiceContext.loadingDrafts = true;
        if (showsDrafts) {
            this.beginLoading();
        }
        try {
            this.invoiceContext.draftPage = await this.invoiceService.getDocuments({...this.query, draft: true });
            if (this.invoiceContext.activeBox === 'DRAFTS') {
                this.invoiceContext.invoicePage = this.invoiceContext.draftPage;
            }
        } finally {
            this.invoiceContext.loadingDrafts = false;
            if (showsDrafts) {
                this.endLoading();
            }
        }
    }

    @watch((vm) => [vm.query.invoiceReference, vm.query.partnerName])
    queryChanged() {
        this.query.pageable.page = 0;
        this.reloadCurrentView();
    }

    loadInvoices() {
        this.beginLoading();
        this.invoiceService.getDocuments({
            ...this.query,
            draft: this.invoiceContext.activeBox === 'DRAFTS'
        }).then(page => this.invoiceContext.invoicePage = page)
        .finally(() => this.endLoading());
    }

    changeDocType(value: DocumentType) {
        this.query.type = value;
        this.query.pageable.page = 0;
        this.reloadCurrentView();
    }

    changeStatus(value: StatusFilter) {
        this.statusFilter = value;
        this.query.paid = value === 'PAID' ? true : value === 'UNPAID' ? false : undefined;
        this.query.overdue = value === 'OVERDUE' ? true : undefined;
        this.query.errored = value === 'ERROR' ? true : undefined;
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
        this.router.load(currentOwnershipRoute(`/invoices/${item.id}`));
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
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.invoice.draft-delete-overview-failed')});
        }
        return false;
    }

    formatDate(date) {
        return moment(date).format('D/M/YYYY');
    }

    get amountSortProperty(): string {
        return this.vatMode === 'incl' ? 'amountInclVat' : 'amountExclVat';
    }

    totalAmount(item: DocumentDto): number | undefined {
        return this.vatMode === 'incl' ? item.amountInclVat : item.amountExclVat;
    }

    async markPaid(event: Event, item: DocumentDto) {
        event.stopPropagation();
        try {
            const datePaid = moment().toISOString();
            await this.invoiceService.togglePaidDocument(item.id);
            if (item.paidOn) {
                item.paidOn = undefined;
                this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr(`alert.invoice.marked-unpaid.${baseDocumentType(item.type)}`)});
            } else {
                item.paidOn = datePaid;
                this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr(`alert.invoice.marked-paid.${baseDocumentType(item.type)}`)});
            }
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.invoice.paid-status-failed')});
        }
    }

    markErrorSeen(event: Event, item: DocumentDto) {
        event.stopPropagation();
        return this.invoiceContext.markErrorSeen(item);
    }

    async resendErrored(event: Event, item: DocumentDto) {
        event.stopPropagation();
        const draft = await this.invoiceContext.createDraftFromErrored(item);
        if (draft) {
            this.router.load(currentOwnershipRoute(`/invoices/${draft.id}`));
        }
    }

    async duplicate(event: Event, item: DocumentDto) {
        event.stopPropagation();
        if (isSelfBillingType(item.type)) return;
        try {
            const ubl = item.ubl ?? (await this.invoiceService.getDocument(item.id)).ubl;
            if (!ubl) {
                this.ea.publish('alert', {alertType: AlertType.Warning, text: this.i18n.tr('alert.invoice.no-ubl-data')});
                return;
            }
            const type = item.type === DocumentType.CREDIT_NOTE ? DocumentType.CREDIT_NOTE : DocumentType.INVOICE;
            const source = type === DocumentType.CREDIT_NOTE ? parseCreditNote(ubl) : parseInvoice(ubl);
            this.invoiceContext.duplicateDocument(source, type);
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr(`alert.invoice.duplicate-failed.${item.type}`)});
        }
    }

    showUploadUblModal() {
        this.uploadUblModal.showModal();
    }

    private beginLoading() {
        this.pendingLoads++;
        this.invoiceContext.loadingInvoices = true;
    }

    private endLoading() {
        this.pendingLoads = Math.max(0, this.pendingLoads - 1);
        this.invoiceContext.loadingInvoices = this.pendingLoads > 0;
    }

    private reloadCurrentView() {
        if (this.invoiceContext.activeBox === 'DRAFTS') {
            this.loadDrafts();
            return;
        }
        this.loadInvoices();
    }

    private updateVatDisplayMode(preferredMode: VatDisplayMode = this.vatDisplay.mode) {
        const vatNumber = this.companyService.myCompany?.vatNumber;
        const nextMode = getVatDisplayMode(vatNumber, preferredMode);
        if (this.vatMode === nextMode) {
            return;
        }

        const currentSort = this.query.pageable.sort?.[0];
        const sortWasByAmount = currentSort?.property === 'amountInclVat' || currentSort?.property === 'amountExclVat';
        this.vatMode = nextMode;

        if (!sortWasByAmount || !currentSort) {
            return;
        }

        currentSort.property = this.amountSortProperty;
        this.activeSortProperty = this.amountSortProperty;
        this.reloadCurrentView();
    }

    private sortDirection(property: string): SortDirection | undefined {
        return this.query.pageable.sort?.find(sort => sort.property === property)?.direction;
    }
}

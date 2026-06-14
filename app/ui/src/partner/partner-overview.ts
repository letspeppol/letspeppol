import {resolve} from "@aurelia/kernel";
import {PartnerDto, PartnerService} from "../services/app/partner-service";
import {PartnerContext} from "./partner-context";
import {IEventAggregator, watch} from "aurelia";
import {AlertType} from "../components/alert/alert";
import {I18N} from "@aurelia/i18n";

type SortDirection = "asc" | "desc";

export class PartnerOverview {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private partnerContext = resolve(PartnerContext);
    private partnerService = resolve(PartnerService);
    private readonly i18n = resolve(I18N);
    searchQuery = '';
    category = 'all';
    activeSortProperty = 'name';
    activeSortDirection: SortDirection = 'asc';
    pageSize = 20;
    currentPage = 0;
    allCount = 0;
    customerCount = 0;
    supplierCount = 0;

    attached() {
        this.loadPartners();
    }

    @watch((vm) => vm.partnerContext.partners.length)
    partnersChange() {
        this.updateCounts();
        this.filterItems(this.category);
    }

    searchQueryChanged() {
        this.currentPage = 0;
    }

    async loadPartners() {
        this.partnerContext.partners = await this.partnerService.getPartners();
        this.updateCounts();
    }

    filterItems(category) {
        this.category = category;
        this.currentPage = 0;
        switch (category) {
            case 'all':
                this.partnerContext.filteredPartners = this.partnerContext.partners;
                break;
            case 'suppliers':
                this.partnerContext.filteredPartners = this.partnerContext.partners.filter(partner => partner.supplier);
                break;
            case 'customers':
                this.partnerContext.filteredPartners = this.partnerContext.partners.filter(partner => partner.customer);
                break;
        }
    }

    get visiblePartners() {
        const start = this.currentPage * this.pageSize;
        return this.sortedFilteredPartners.slice(start, start + this.pageSize);
    }

    get visiblePartnerCount() {
        return this.sortedFilteredPartners.length;
    }

    get totalPages() {
        return Math.max(1, Math.ceil(this.visiblePartnerCount / this.pageSize));
    }

    get pageStart() {
        if (!this.visiblePartnerCount) {
            return 0;
        }
        return (this.currentPage * this.pageSize) + 1;
    }

    get pageEnd() {
        return Math.min((this.currentPage + 1) * this.pageSize, this.visiblePartnerCount);
    }

    get sortedFilteredPartners() {
        const query = this.searchQuery.toLowerCase();
        const filtered = (this.partnerContext.filteredPartners ?? [])
            .filter(partner => partner.name.toLowerCase().includes(query));

        return [...filtered].sort((a, b) => this.comparePartners(a, b));
    }

    toggleSort(property: string) {
        if (this.activeSortProperty === property) {
            this.activeSortDirection = this.activeSortDirection === 'asc' ? 'desc' : 'asc';
        } else {
            this.activeSortProperty = property;
            this.activeSortDirection = 'asc';
        }
        this.currentPage = 0;
    }

    nextPage() {
        if (this.currentPage >= this.totalPages - 1) {
            return;
        }
        this.currentPage++;
    }

    previousPage() {
        if (this.currentPage <= 0) {
            return;
        }
        this.currentPage--;
    }

    selectItem(partner: PartnerDto) {
        this.partnerContext.selectedPartner = partner;
    }

    async deleteItem(event: Event, partner: PartnerDto) {
        event.stopPropagation();
        try {
            await this.partnerService.deletePartner(partner.id)
            this.partnerContext.deletePartner(partner);
            this.updateCounts();
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('alert.partner.deleted')});
        } catch (e) {
            console.log(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.partner.delete-failed')});
        }
        return false;
    }

    private comparePartners(a: PartnerDto, b: PartnerDto) {
        const left = this.partnerValue(a, this.activeSortProperty);
        const right = this.partnerValue(b, this.activeSortProperty);
        const direction = this.activeSortDirection === 'asc' ? 1 : -1;

        if (typeof left === 'number' && typeof right === 'number') {
            return (left - right) * direction;
        }

        return String(left ?? '').localeCompare(String(right ?? ''), undefined, {numeric: true, sensitivity: 'base'}) * direction;
    }

    private partnerValue(partner: PartnerDto, property: string) {
        switch (property) {
            case 'name':
                return partner.name;
            case 'address':
                return `${partner.registeredOffice?.street ?? ''} ${partner.registeredOffice?.houseNumber ?? ''}`.trim();
            case 'postalCode':
                return partner.registeredOffice?.postalCode;
            case 'city':
                return partner.registeredOffice?.city;
            case 'countryCode':
                return partner.registeredOffice?.countryCode;
            case 'vatNumber':
                return partner.vatNumber;
            default:
                return '';
        }
    }

    private updateCounts() {
        const partners = this.partnerContext.partners ?? [];
        this.allCount = partners.length;
        this.customerCount = partners.filter(partner => partner.customer).length;
        this.supplierCount = partners.filter(partner => partner.supplier).length;
    }
}

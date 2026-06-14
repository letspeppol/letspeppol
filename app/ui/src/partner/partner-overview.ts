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

    attached() {
        this.loadPartners();
    }

    @watch((vm) => vm.partnerContext.partners.length)
    partnersChange() {
        this.filterItems(this.category);
    }

    async loadPartners() {
        this.partnerContext.partners = await this.partnerService.getPartners();
    }

    filterItems(category) {
        this.category = category;
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
    }

    selectItem(partner: PartnerDto) {
        this.partnerContext.selectedPartner = partner;
    }

    async deleteItem(event: Event, partner: PartnerDto) {
        event.stopPropagation();
        try {
            await this.partnerService.deletePartner(partner.id)
            this.partnerContext.deletePartner(partner);
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
}

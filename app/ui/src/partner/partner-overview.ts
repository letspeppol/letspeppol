import {resolve} from "@aurelia/kernel";
import {PartnerDto, PartnerService} from "../services/app/partner-service";
import {PartnerContext} from "./partner-context";
import {IEventAggregator, watch} from "aurelia";
import {AlertType} from "../components/alert/alert";
import {I18N} from "@aurelia/i18n";

export class PartnerOverview {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private partnerContext = resolve(PartnerContext);
    private partnerService = resolve(PartnerService);
    private readonly i18n = resolve(I18N);
    searchQuery = '';
    category = 'all'

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
}

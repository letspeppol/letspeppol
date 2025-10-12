import {resolve} from "@aurelia/kernel";
import {PartnerResponse, PartnerService} from "../services/partner-service";
import {PartnerContext} from "./partner-context";
import {IEventAggregator, watch} from "aurelia";
import {AlertType} from "../alert/alert";

export class PartnerOverview {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private partnerContext = resolve(PartnerContext);
    private partnerService = resolve(PartnerService);
    searchQuery = '';
    category = 'all'

    attached() {
        this.loadPartners();
    }

    @watch((vm) => vm.partnerContext.partners.length)
    partnersChange() {
        console.log('jop');
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

    selectItem(partner: PartnerResponse) {
        this.partnerContext.selectedPartner = partner;
    }

    async deleteItem(event: Event, partner: PartnerResponse) {
        event.stopPropagation();
        try {
            await this.partnerService.deletePartner(partner.id)
            this.partnerContext.deletePartner(partner);
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Partner deleted"});
        } catch (e) {
            console.log(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to delete partner"});
        }
        return false;
    }
}

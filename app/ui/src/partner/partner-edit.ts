import {AlertType} from "../components/alert/alert";
import {IEventAggregator} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {PartnerService} from "../services/app/partner-service";
import {PartnerContext} from "./partner-context";

export class PartnerEdit {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly partnerService = resolve(PartnerService);
    private readonly partnerContext = resolve(PartnerContext);

    async savePartner() {
        try {
            let successMessage = "Partner updated successfully";
            if (this.partnerContext.selectedPartner.id) {
                await this.partnerService.updatePartner(this.partnerContext.selectedPartner.id, this.partnerContext.selectedPartner);
            } else {
                const partner = await this.partnerService.createPartner(this.partnerContext.selectedPartner);
                this.partnerContext.addPartner(partner);
                successMessage = "Partner created successfully";
            }
            this.ea.publish('alert', {alertType: AlertType.Success, text: successMessage});
            this.partnerContext.selectedPartner = undefined;
        } catch(e) {
            console.error(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to update account"});
        }
    }


}

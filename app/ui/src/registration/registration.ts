import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {KycCompanyResponse, RegistrationService} from "../services/registration-service";
import {AlertType} from "../alert/alert";

export class Registration {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private registrationService = resolve(RegistrationService);
    step = 0;
    email: string | undefined;
    companyNumber : string | undefined;
    company : KycCompanyResponse | undefined;

    async checkCompanyNumber() {
        try {
            this.company = await this.registrationService.getCompany(this.companyNumber);
            this.step++;
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Token invalid"});
        }
    }

    restart(e) {
        this.companyNumber = undefined;
        this.company = undefined;
        this.step = 0;
        e.preventDefault();
    }

    async confirmCompany() {
        try {
            await this.registrationService.confirmCompany(this.companyNumber, this.email);
            this.step++;
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Company already registered"});
        }
    }

}
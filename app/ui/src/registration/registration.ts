import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {KycCompanyResponse, RegistrationService} from "../services/kyc/registration-service";

export class Registration {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private registrationService = resolve(RegistrationService);
    step = 0;
    email: string | undefined;
    companyNumber : string | undefined;
    company : KycCompanyResponse | undefined;
    errorCode: string | undefined;

    async checkCompanyNumber() {
        this.errorCode = undefined;
        try {
            this.ea.publish('showOverlay', "Searching company");
            this.company = await this.registrationService.getCompany(this.companyNumber);
            this.step++;
        } catch {
            this.errorCode = "registration-company-not-found";
        } finally {
            this.ea.publish('hideOverlay');
        }
    }

    restart(e) {
        this.errorCode = undefined;
        this.companyNumber = undefined;
        this.company = undefined;
        this.step = 0;
        e.preventDefault();
    }

    async confirmCompany() {
        this.errorCode = undefined;
        try {
            this.ea.publish('showOverlay', "Confirming registration request");
            await this.registrationService.confirmCompany(this.companyNumber, this.email);
            this.step++;
        } catch {
            this.errorCode = "registration-company-already-registered";
        } finally {
            this.ea.publish('hideOverlay');
        }
    }

}

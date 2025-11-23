import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {KycCompanyResponse, RegistrationService} from "../services/kyc/registration-service";
import {PeppolDirectoryResponse, PeppolDirService} from "../services/peppol/peppol-dir-service";

export class Registration {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private registrationService = resolve(RegistrationService);
    private peppolDirService = resolve(PeppolDirService);
    step = 0;
    email: string | undefined;
    vatNumber : string | undefined;
    company : KycCompanyResponse | undefined;
    errorCode: string | undefined;

    async checkVatNumber() {
        this.errorCode = undefined;
        try {
            this.ea.publish('showOverlay', "Searching company");
            const peppolId = "0208:" + this.vatNumber.replace(/\D/g, '');
            this.company = await this.registrationService.getCompany(peppolId);
            this.step++;
        } catch {
            this.errorCode = "registration-company-not-found";
        } finally {
            this.ea.publish('hideOverlay');
        }
    }

    restart(e) {
        this.errorCode = undefined;
        this.vatNumber = undefined;
        this.company = undefined;
        this.step = 0;
        e.preventDefault();
    }

    async confirmCompany() {
        this.errorCode = undefined;
        try {
            this.ea.publish('showOverlay', "Confirming registration request");
            const peppolDirectoryResponse = await this.peppolDirService.findByParticipant(this.company.peppolId);
            if (peppolDirectoryResponse.matches.length > 0) {
                this.errorCode = "registration-company-already-registered-on-peppol";
                return;
            }
            await this.registrationService.confirmCompany(this.company.peppolId, this.email);
            this.step++;
        } catch(e) {
            console.log(e);
            this.errorCode = "registration-company-already-registered";
        } finally {
            this.ea.publish('hideOverlay');
        }
    }

}

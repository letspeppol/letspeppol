import { resolve } from "@aurelia/kernel";
import { IEventAggregator } from "aurelia";
import { KycCompanyResponse, RegistrationService } from "../services/kyc/registration-service";
import { IRouter } from '@aurelia/router';

export class Registration {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private registrationService = resolve(RegistrationService);
    private readonly router = resolve(IRouter);
    step = 0;
    email: string | undefined;
    vatNumber: string | undefined;
    company: KycCompanyResponse | undefined;
    errorCode: string | undefined;

    async checkVatNumber() {
        this.errorCode = undefined;
        try {
            this.ea.publish('showOverlay', "Searching company");
            const digits = (this.vatNumber ?? '').replace(/\D/g, '');
            const companyNumber = digits.slice(-10).padStart(10, '0');
            const peppolId = `0208:${companyNumber}`;
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
            await this.registrationService.confirmCompany(this.company.peppolId, this.email);
            this.step++;
        } catch (e) {
            console.log(e);
            if (e instanceof Response) {
                try {
                    const error = await e.json() as { errorCode: string };
                    if (error.errorCode === 'company_already_registered') {
                        this.errorCode = "registration-company-already-registered";
                    } else if (error.errorCode === 'account_already_linked') {
                        this.errorCode = "registration-account-already-linked";
                    } else {
                        this.errorCode = "registration-error";
                    }
                } catch {
                    this.errorCode = "registration-error";
                }
            } else {
                this.errorCode = "registration-error";
            }
        } finally {
            this.ea.publish('hideOverlay');
        }
    }

    goToOnboarding() {
        void this.router.load('/onboarding');
    }
}

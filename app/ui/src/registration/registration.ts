import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {KycCompanyResponse, RegistrationAccountType, RegistrationService} from "../services/kyc/registration-service";
import {IRouter, Params, RouteNode} from '@aurelia/router';

export class Registration {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private registrationService = resolve(RegistrationService);
    private readonly router = resolve(IRouter);
    registrationType: RegistrationAccountType = 'ADMIN';
    step = 0;
    email: string | undefined;
    vatNumber : string | undefined;
    company : KycCompanyResponse | undefined;
    errorCode: string | undefined;

    loading(params: Params, next: RouteNode) {
        this.registrationType = next.data?.registrationType === 'AFFILIATE' ? 'AFFILIATE' : 'ADMIN';
    }

    get titleStep0Key() {
        return this.registrationType === 'AFFILIATE' ? 'registration.affiliate-title-step0' : 'registration.title-step0';
    }

    get titleStep1Key() {
        return this.registrationType === 'AFFILIATE' ? 'registration.affiliate-title-step1' : 'registration.title-step1';
    }

    get introKey() {
        return this.registrationType === 'AFFILIATE' ? 'registration.affiliate-intro' : 'confirmation.belgian-only';
    }

    get createAccountKey() {
        return this.registrationType === 'AFFILIATE' ? 'registration.affiliate-create-account' : 'registration.create-account';
    }

    get alreadyRegisteredKey() {
        return this.registrationType === 'AFFILIATE' ? 'registration.affiliate-already-registered' : 'registration.already-registered';
    }

    async checkVatNumber() {
        this.errorCode = undefined;
        try {
            this.ea.publish('showOverlay', "Searching company");
            const digits = (this.vatNumber ?? '').replace(/\D/g, '');
            const companyNumber = digits.slice(-10).padStart(10, '0');
            const peppolId = `0208:${companyNumber}`;
            this.company = await this.registrationService.getCompany(peppolId);
            if (this.registrationType === 'AFFILIATE' && this.company.hasAdmin) {
                this.errorCode = "registration-company-affiliate-contact-admin";
                this.company = undefined;
                return;
            }
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
            await this.registrationService.confirmCompany({
                type: this.registrationType,
                peppolId: this.company.peppolId,
                email: this.email,
                city: this.company.city,
                postalCode: this.company.postalCode,
                street: this.company.street
            });
            this.step++;
        } catch(e) {
            console.log(e);
            this.errorCode = this.registrationType === 'AFFILIATE'
                ? "registration-company-affiliate-contact-admin"
                : "registration-company-already-registered";
        } finally {
            this.ea.publish('hideOverlay');
        }
    }

    goToOnboarding() {
        void this.router.load(this.registrationType === 'AFFILIATE' ? '/onboarding?flow=affiliate' : '/onboarding');
    }
}

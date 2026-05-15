import {Params, RouteNode} from "@aurelia/router";
import {resolve} from "@aurelia/kernel";
import * as webeid from '@web-eid/web-eid-library/web-eid';
import {IEventAggregator} from "aurelia";
import {AlertType} from "../components/alert/alert";
import {RegistrationAccountType} from "../services/kyc/registration-service";

export class Onboarding {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private certificate = null;
    private signatureAlgorithm = null;
    registrationType: RegistrationAccountType = 'ADMIN';
    registrationUrl = '/registration';
    registrationLinkTextKey = 'onboarding.step.register-company';

    loading(params: Params, next: RouteNode) {
        this.registrationType = next.queryParams.get('flow') === 'affiliate' ? 'AFFILIATE' : 'ADMIN';
        this.registrationUrl = this.registrationType === 'AFFILIATE' ? '/affiliate/registration' : '/registration';
        this.registrationLinkTextKey = this.registrationType === 'AFFILIATE'
            ? 'onboarding.step.register-affiliate'
            : 'onboarding.step.register-company';
    }

    public async checkWebEID() {
            try {
                const {
                    certificate,
                    supportedSignatureAlgorithms
                } = await webeid.getSigningCertificate({lang: 'en'});

                this.certificate = certificate;
                this.signatureAlgorithm = supportedSignatureAlgorithms.find(item => item.hashFunction === "SHA-256");
            } catch (error) {
                let text = "Confirming Identity failed";
                if (error instanceof Response) {
                    try {
                        const j = await error.json();
                        text = j?.message ?? `${error.status} ${error.statusText}`;
                    } catch {
                        text = `${error.status} ${error.statusText}`;
                    }
                } else if (error && typeof error === "object" && "message" in error) {
                    text = String((error as any).message);
                }
                this.ea.publish('alert', { alertType: AlertType.Danger, text });
            }

            return;
        }
}

import {Params, RouteNode} from "@aurelia/router";
import {resolve} from "@aurelia/kernel";
import * as webeid from '@web-eid/web-eid-library/web-eid';
import {IEventAggregator} from "aurelia";
import {AlertType} from "../components/alert/alert";
import {KYCApi} from "../services/kyc/kyc-api";
import {
    Director,
    PrepareSigningResponse,
    RegistrationService,
    TokenVerificationResponse
} from "../services/kyc/registration-service";
import {SignatureAlgorithm} from "@web-eid/web-eid-library/models/SignatureAlgorithm";
import {LibrarySignResponse} from "@web-eid/web-eid-library/models/message/LibraryResponse";

export class EmailConfirmation {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    readonly kycApi = resolve(KYCApi);
    readonly registrationService = resolve(RegistrationService);
    public errorMessage: string | undefined; // made public for template binding
    public emailToken: string;
    public tokenVerificationResponse: TokenVerificationResponse | undefined; // made public for template binding
    public confirmedDirector: Director | undefined; // holds the chosen director
    private password;
    private passwordDuplicate;
    private registrationSuccess = true;
    private agreedToContract = false;
    private step = 0;
    private certificate;
    private signatureAlgorithm;
    private prepareSigningResponse;

    public loading(params: Params, next: RouteNode) {
        this.emailToken = next.queryParams.get('token');
        if (!this.emailToken) {
            this.errorMessage = 'Token not available';
            return;
        }
        this.registrationService.verifyToken(this.emailToken).then(result => {
            this.tokenVerificationResponse = result;
            this.step = 1;
        }).catch(error => {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Token invalid"});
        });
    }

    getContractUrl() {
        return `${this.kycApi.httpClient.baseUrl}/api/register/contract/${this.confirmedDirector.id}?token=${this.emailToken}#page=1&view=FitH,300`;
    }

    public async confirmContract() {
        try {
//             const {
//                 certificate,
//                 supportedSignatureAlgorithms
//             } = await webeid.getSigningCertificate({lang: 'en'});
//
//             const {
//                 signatureAlgorithm,
//                 prepareSigningResponse
//             } = await this.prepareSigning(supportedSignatureAlgorithms, certificate);
            const certificate = this.certificate;
            const signatureAlgorithm = this.signatureAlgorithm;
            const prepareSigningResponse = this.prepareSigningResponse;

            const signResponse = await webeid.sign(
                certificate,
                prepareSigningResponse.hashToSign,
                signatureAlgorithm.hashFunction
            );
            const finalizeSigningResponse = await this.finalizeSigning(certificate, signResponse, prepareSigningResponse);
            this.step = 3;
            await this.downloadFile(finalizeSigningResponse);
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Signed contract downloaded!"});
        } catch (error) {
            const json = await error.json();
            this.ea.publish('alert', {alertType: AlertType.Danger, text: json ? json.message : "Signing failed"}); // TODO error util
        }

    }

    private async prepareSigning(supportedSignatureAlgorithms: Array<SignatureAlgorithm>, certificate: string) {
        const signatureAlgorithm = supportedSignatureAlgorithms.find(item => item.hashFunction === "SHA-256");

        const prepareSigningRequest = {
            emailToken: this.emailToken,
            directorId: this.confirmedDirector.id,
            certificate: certificate,
            supportedSignatureAlgorithms: supportedSignatureAlgorithms,
            language: 'en'
        };
        const prepareSigningResponse = await this.registrationService.prepareSign(prepareSigningRequest);
        return {signatureAlgorithm, prepareSigningResponse};
    }

    private async finalizeSigning(certificate: string, signResponse: LibrarySignResponse, prepareSigningResponse: PrepareSigningResponse) {
        const finalizeSigningRequest = {
            emailToken: this.emailToken,
            directorId: this.confirmedDirector.id,
            certificate: certificate,
            signature: signResponse.signature,
            signatureAlgorithm: signResponse.signatureAlgorithm,
            hashToSign: prepareSigningResponse.hashToSign,
            hashToFinalize: prepareSigningResponse.hashToFinalize,
            password: this.password
        };
        return await this.registrationService.finalizeSign(finalizeSigningRequest);
    }

    async downloadFile(response: Response) {
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);

        const a = document.createElement("a");
        a.href = url;
        a.download = "contract_signed.pdf";
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
    }

    public async confirmDirector(director: Director) {
        if (this.confirmedDirector) return; // already confirmed one
        this.confirmedDirector = director;
        try {
            const {
                certificate,
                supportedSignatureAlgorithms
            } = await webeid.getSigningCertificate({lang: 'en'});

            const {
                signatureAlgorithm,
                prepareSigningResponse
            } = await this.prepareSigning(supportedSignatureAlgorithms, certificate);

            this.certificate = certificate;
            this.signatureAlgorithm = signatureAlgorithm;
            this.prepareSigningResponse = prepareSigningResponse;
            this.step = 2;
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
            this.confirmedDirector = undefined;
        }

        return;
    }
}

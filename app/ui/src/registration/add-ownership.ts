import {resolve} from "@aurelia/kernel";
import {IRouter} from "@aurelia/router";
import * as webeid from '@web-eid/web-eid-library/web-eid';
import {IEventAggregator} from "aurelia";
import {AlertType} from "../components/alert/alert";
import {
    Director,
    KycCompanyResponse,
    PrepareSigningResponse,
    RegistrationService
} from "../services/kyc/registration-service";
import {LibrarySignResponse} from "@web-eid/web-eid-library/models/message/LibraryResponse";
import {SignatureAlgorithm} from "@web-eid/web-eid-library/models/SignatureAlgorithm";
import {OwnershipService} from "../services/app/ownership-service";
import {CompanyService} from "../services/app/company-service";

export class AddOwnership {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly registrationService = resolve(RegistrationService);
    private readonly ownershipService = resolve(OwnershipService);
    private readonly companyService = resolve(CompanyService);
    private readonly router = resolve(IRouter);

    vatNumber: string | undefined;
    company: KycCompanyResponse | undefined;
    confirmedDirector: Director | undefined;
    subscriberEmail = '';
    errorCode: string | undefined;
    warningKey: string | undefined;
    alreadyRegisteredProvider = '';
    step = 0;
    agreedToContract = false;
    confirmInProgress = false;
    prepareSigningResponse: PrepareSigningResponse | null = null;
    private certificate: string | null = null;
    private signatureAlgorithm: SignatureAlgorithm | null = null;

    attached() {
        if (this.ownershipService.getCurrentOwnershipType() !== 'ADMIN') {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Only an admin can add another account"});
            void this.router.load('/dashboard');
            return;
        }
        void this.loadCurrentAccountInfo();
    }

    async loadCurrentAccountInfo() {
        const company = this.companyService.myCompany ?? await this.companyService.getAndSetMyCompanyForToken();
        this.subscriberEmail = company.subscriberEmail ?? '';
    }

    async checkVatNumber() {
        this.errorCode = undefined;
        this.warningKey = undefined;
        this.company = undefined;
        this.confirmedDirector = undefined;
        this.step = 0;
        this.alreadyRegisteredProvider = '';

        try {
            this.ea.publish('showOverlay', "Searching company");
            const digits = (this.vatNumber ?? '').replace(/\D/g, '');
            const companyNumber = digits.slice(-10).padStart(10, '0');
            const peppolId = `0208:${companyNumber}`;
            this.company = await this.registrationService.getCompany(peppolId);

            if (this.company.hasAdmin) {
                this.warningKey = 'ownershipAdd.company-has-admin';
                return;
            }

            this.step = 1;
        } catch {
            this.errorCode = "registration-company-not-found";
        } finally {
            this.ea.publish('hideOverlay');
        }
    }

    restart(event?: Event) {
        this.errorCode = undefined;
        this.warningKey = undefined;
        this.vatNumber = undefined;
        this.company = undefined;
        this.confirmedDirector = undefined;
        this.agreedToContract = false;
        this.step = 0;
        this.alreadyRegisteredProvider = '';
        this.prepareSigningResponse = null;
        this.certificate = null;
        this.signatureAlgorithm = null;
        event?.preventDefault();
    }

    getContractUrl() {
        if (!this.company || !this.confirmedDirector) {
            return '';
        }
        const contractUrl = this.registrationService.getContractUrl(this.company.peppolId, this.confirmedDirector.id);
        return `${contractUrl}#page=1&view=FitH,300`;
    }

    async confirmDirector(director: Director) {
        if (!this.company || this.confirmedDirector) {
            return;
        }

        this.confirmedDirector = director;
        try {
            const {
                certificate,
                supportedSignatureAlgorithms
            } = await webeid.getSigningCertificate({lang: 'en'});

            const signatureAlgorithm = supportedSignatureAlgorithms.find(item => item.hashFunction === "SHA-256");
            if (!signatureAlgorithm) {
                throw new Error("No supported SHA-256 signature algorithm found");
            }

            const prepareSigningResponse = await this.registrationService.prepareSign({
                peppolId: this.company.peppolId,
                directorId: director.id,
                certificate,
                supportedSignatureAlgorithms,
                language: 'en'
            });

            this.certificate = certificate;
            this.signatureAlgorithm = signatureAlgorithm;
            this.prepareSigningResponse = prepareSigningResponse;
            this.step = 2;
        } catch (error) {
            let text = "Confirming identity failed";
            if (error instanceof Response) {
                try {
                    const j = await error.json();
                    text = j?.message ?? `${error.status} ${error.statusText}`;
                } catch {
                    text = `${error.status} ${error.statusText}`;
                }
            } else if (error && typeof error === "object" && "message" in error) {
                text = String((error as {message: string}).message);
            }
            this.ea.publish('alert', {alertType: AlertType.Danger, text});
            this.confirmedDirector = undefined;
        }
    }

    async confirmContract() {
        if (!this.company || !this.confirmedDirector || !this.certificate || !this.signatureAlgorithm || !this.prepareSigningResponse) {
            return;
        }

        this.confirmInProgress = true;
        try {
            const signResponse = await webeid.sign(
                this.certificate,
                this.prepareSigningResponse.hashToSign,
                this.signatureAlgorithm.hashFunction
            );
            const finalizeSigningResponse = await this.finalizeSigning(signResponse);
            const registrationStatus = finalizeSigningResponse.headers.get('Registration-Status');
            switch (registrationStatus) {
                case 'UNKNOWN':
                case 'FAILED':
                    this.warningKey = 'account.registration-failed.try-again-one-day';
                    break;
                case 'SUSPENDED':
                    this.warningKey = 'account.registration-failed.contact-us';
                    break;
                case 'CONFLICT': {
                    const raw = finalizeSigningResponse.headers.get('Registration-Provider') ?? '';
                    this.alreadyRegisteredProvider = raw ? decodeURIComponent(raw) : '';
                    this.warningKey = 'account.registration-failed.contact-provider';
                    break;
                }
                default:
                    break;
            }
            await this.downloadFile(finalizeSigningResponse);
            await this.ownershipService.loadOwnerships();
            this.step = 3;
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Account added successfully"});
        } catch (error) {
            let text = "Signing contract failed";
            if (error instanceof Response) {
                try {
                    const j = await error.json();
                    text = j?.message ?? `${error.status} ${error.statusText}`;
                } catch {
                    text = `${error.status} ${error.statusText}`;
                }
            } else if (error && typeof error === "object" && "message" in error) {
                text = String((error as {message: string}).message);
            }
            this.ea.publish('alert', {alertType: AlertType.Danger, text});
            this.confirmInProgress = false;
        }
    }

    private finalizeSigning(signResponse: LibrarySignResponse): Promise<Response> {
        const company = this.company!;
        const director = this.confirmedDirector!;
        const certificate = this.certificate!;
        const prepareSigningResponse = this.prepareSigningResponse!;

        return this.registrationService.finalizeSign({
            peppolId: company.peppolId,
            directorId: director.id,
            email: null,
            certificate,
            signature: signResponse.signature,
            signatureAlgorithm: signResponse.signatureAlgorithm,
            hashToSign: prepareSigningResponse.hashToSign,
            hashToFinalize: prepareSigningResponse.hashToFinalize
        });
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

    async goToDashboard() {
        await this.router.load('/dashboard');
    }
}

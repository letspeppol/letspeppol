import {resolve} from "@aurelia/kernel";
import {SignatureAlgorithm} from "@web-eid/web-eid-library/models/SignatureAlgorithm";
import {KYCApi} from "./kyc-api";
import {LoginService} from "../app/login-service";

export interface TokenVerificationResponse {
    email: string;
    accountExists: boolean;
    accountVerified: boolean;
    directorSigned: boolean;
    requestedType: string;
    company: KycCompanyResponse;
    requester?: unknown;
}

export interface KycCompanyResponse {
    id: number,
    peppolId: string,
    vatNumber: string,
    name: string;
    street: string;
    city: string;
    postalCode: string;
    directors?: Director[];
}

export interface Director {
    id: number;
    name: string;
}

export interface PrepareSigningRequest {
    peppolId: string,
    directorId: number,
    certificate: string,
    supportedSignatureAlgorithms: Array<SignatureAlgorithm>,
    language: string,
}

export interface PrepareSigningResponse {
    hashToSign: string,
    hashToFinalize: string,
    hashFunction: string,
    allowedToSign: boolean
}

export interface FinalizeSigningRequest {
    peppolId: string,
    directorId: number,
    email: string,
    certificate: string,
    signature: string,
    signatureAlgorithm: SignatureAlgorithm,
    hashToSign: string,
    hashToFinalize: string
}

export interface VerifyAccountRequest {
    token: string,
    newPassword: string
}

export class RegistrationService {
    public kycApi = resolve(KYCApi);
    private loginService = resolve(LoginService);

    async getCompany(peppolId: string): Promise<KycCompanyResponse>  {
        const response = await this.kycApi.httpClient.get(`/api/register/company/${peppolId}`);
        return response.json();
    }

    async confirmCompany(peppolId: string, email: string) {
        const body = {
            peppolId: peppolId,
            email: email
        };
        const response = await this.kycApi.httpClient.post(`/api/register/confirm-company`, JSON.stringify(body) );
        return response.json();
    }

    async verifyToken(token: string) : Promise<TokenVerificationResponse> {
        const response = await this.kycApi.httpClient.post(`/api/register/verify?token=${token}`);
        return response.json();
    }

    async prepareSign(request: PrepareSigningRequest) : Promise<PrepareSigningResponse> {
        const response = await this.kycApi.httpClient.post(`/api/identity/sign/prepare`, JSON.stringify(request));
        return response.json();
    }

    getContractUrl(peppolId: string, directorId: number): string {
        return `${this.kycApi.httpClient.baseUrl}/api/identity/contract/${encodeURIComponent(peppolId)}/${directorId}`;
    }

    async finalizeSign(request: FinalizeSigningRequest) : Promise<Response> {
        return await this.kycApi.httpClient.post(`/api/identity/sign/finalize`, JSON.stringify(request));
    }

    async verifyAccount(request: VerifyAccountRequest): Promise<Response> {
        return await this.kycApi.httpClient.post(`/api/register/verify-account`, JSON.stringify(request));
    }

    async unregisterCompany(): Promise<boolean> {
        const response = await this.kycApi.httpClient.fetch('/sapi/company/peppol/unregister', { method: 'POST' }); //Using fetch to expose response header
        if (response.status === 204) {
            console.log("Was already unregistered");
            return false;
        }
        const token = await response.text();
        if (response.ok && token?.trim()) {
            this.loginService.updateToken(token.trim());
            return false;
        }
        return true;
    }

    async registerCompany(): Promise<boolean> {
        const response = await this.kycApi.httpClient.fetch('/sapi/company/peppol/register', { method: 'POST' }); //Using fetch to expose response header
        if (response.status === 204) {
            console.log("Was already registered");
            return true;
        }
        if (response.ok) {
            const token = await response.text();
            if (token?.trim()) {
                this.loginService.updateToken(token.trim());
                return true;
            }
            return false;
        }
        throw Error(response);
    }

    async downloadSignedContract(): Promise<Response> {
        return this.kycApi.httpClient.get('/sapi/company/signed-contract');
    }

}

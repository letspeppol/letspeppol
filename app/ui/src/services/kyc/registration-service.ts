import {resolve} from "@aurelia/kernel";
import {SignatureAlgorithm} from "@web-eid/web-eid-library/models/SignatureAlgorithm";
import {KYCApi} from "./kyc-api";
import {LoginService} from "../app/login-service";

export type RegistrationAccountType = 'ADMIN' | 'AFFILIATE';

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
    identifier: string,
    vatNumber: string,
    name: string;
    street: string;
    city: string;
    postalCode: string;
    directors?: Director[];
    hasAdmin?: boolean;
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
    email: string | null,
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

export interface ConfirmCompanyRequest {
    type: RegistrationAccountType,
    peppolId: string,
    email: string,
    city?: string,
    postalCode?: string,
    street?: string
}

export class RegistrationService {
    public kycApi = resolve(KYCApi);
    private loginService = resolve(LoginService);

    async getCompany(peppolId: string): Promise<KycCompanyResponse>  {
        const response = await this.kycApi.httpClient.get(`/api/register/company/${peppolId}`);
        return response.json();
    }

    async confirmCompany(request: ConfirmCompanyRequest) {
        const response = await this.kycApi.httpClient.post(`/api/register/confirm-company`, JSON.stringify(request) );
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
        const response = await this.kycApi.httpClient.fetch('/sapi/company/peppol/unregister', { method: 'POST' });
        if (response.status === 204) {
            console.log("Was already unregistered");
            return false;
        }
        if (response.ok) {
            // peppolActive flipped, so the token's claims are stale; KYC mints tokens now, hence a
            // silent re-authorization rather than a token handed back in the response body.
            await this.loginService.refreshToken();
            return false;
        }
        return true;
    }

    async registerCompany(): Promise<boolean> {
        const response = await this.kycApi.httpClient.fetch('/sapi/company/peppol/register', { method: 'POST' });
        if (response.status === 204) {
            console.log("Was already registered");
            return true;
        }
        if (response.ok) {
            await this.loginService.refreshToken();
            return true;
        }
        throw response;
    }

    async downloadSignedContract(): Promise<Response> {
        return this.kycApi.httpClient.get('/sapi/company/signed-contract');
    }

}

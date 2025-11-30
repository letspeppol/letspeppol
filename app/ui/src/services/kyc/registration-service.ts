import {resolve} from "@aurelia/kernel";
import {SignatureAlgorithm} from "@web-eid/web-eid-library/models/SignatureAlgorithm";
import {KYCApi} from "./kyc-api";

export interface TokenVerificationResponse {
    email: string;
    company: KycCompanyResponse;
}

export interface KycCompanyResponse {
    id: number,
    peppolId: string,
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
    emailToken: string,
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
    emailToken: string,
    directorId: number,
    certificate: string,
    signature: string,
    signatureAlgorithm: SignatureAlgorithm,
    hashToSign: string,
    hashToFinalize: string,
    password: string,
}

export class RegistrationService {
    public kycApi = resolve(KYCApi);

    async getCompany(peppolId: string): Promise<KycCompanyResponse>  {
        const response = await this.kycApi.httpClient.get(`/api/company/${peppolId}`);
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

    async finalizeSign(request: FinalizeSigningRequest) : Promise<Response> {
        return await this.kycApi.httpClient.post(`/api/identity/sign/finalize`, JSON.stringify(request));
    }

    async unregisterCompany() {
        await this.kycApi.httpClient.post('/api/company/unregister');
    }
}

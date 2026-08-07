import {resolve} from "@aurelia/kernel";
import {singleton} from "aurelia";
import {KYCApi} from "./kyc-api";
import {deserializeCreationOptions, serializeRegistrationCredential} from "./webauthn-utils";

export interface PasskeyDto {
    id: number;
    displayName: string;
    createdOn: string;
    lastUsedOn: string | null;
}

@singleton()
export class PasskeyService {
    private readonly kycApi = resolve(KYCApi);

    async getRegistrationOptions(displayName: string): Promise<{ challengeToken: string; options: PublicKeyCredentialCreationOptions }> {
        const response = await this.kycApi.httpClient.post('/sapi/passkeys/register/options', JSON.stringify({ displayName }), {
            headers: { 'Content-Type': 'application/json' }
        });
        const json = await response.json();
        return {
            challengeToken: json.challengeToken,
            options: deserializeCreationOptions(json),
        };
    }

    async verifyRegistration(credential: PublicKeyCredential, challengeToken: string, displayName: string): Promise<void> {
        await this.kycApi.httpClient.post('/sapi/passkeys/register/verify', JSON.stringify({
            challengeToken,
            displayName,
            credential: serializeRegistrationCredential(credential),
        }), {
            headers: { 'Content-Type': 'application/json' }
        });
    }

    async listPasskeys(): Promise<PasskeyDto[]> {
        const response = await this.kycApi.httpClient.get('/sapi/passkeys');
        return response.json();
    }

    async deletePasskey(id: number): Promise<void> {
        await this.kycApi.httpClient.delete(`/sapi/passkeys/${id}`);
    }

    async renamePasskey(id: number, displayName: string): Promise<void> {
        await this.kycApi.httpClient.put(`/sapi/passkeys/${id}/name`, JSON.stringify({ displayName }), {
            headers: { 'Content-Type': 'application/json' }
        });
    }
}

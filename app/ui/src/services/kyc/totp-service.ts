import {resolve} from "@aurelia/kernel";
import {singleton} from "aurelia";
import {KYCApi} from "./kyc-api";

export interface TotpSetupResponse {
    secret: string;
    qrCodeDataUri: string;
}

export interface TotpEnableResponse {
    recoveryCodes: string[];
}

export interface TotpStatusResponse {
    enabled: boolean;
}

@singleton()
export class TotpService {
    private readonly kycApi = resolve(KYCApi);

    async getStatus(): Promise<TotpStatusResponse> {
        const response = await this.kycApi.httpClient.get('/sapi/totp/status');
        return response.json();
    }

    async setup(): Promise<TotpSetupResponse> {
        const response = await this.kycApi.httpClient.post('/sapi/totp/setup', null, {
            headers: {'Content-Type': 'application/json'}
        });
        return response.json();
    }

    async enable(code: string): Promise<TotpEnableResponse> {
        const response = await this.kycApi.httpClient.post('/sapi/totp/enable', JSON.stringify({code}), {
            headers: {'Content-Type': 'application/json'}
        });
        return response.json();
    }

    async disable(code: string): Promise<void> {
        await this.kycApi.httpClient.post('/sapi/totp/disable', JSON.stringify({code}), {
            headers: {'Content-Type': 'application/json'}
        });
    }
}

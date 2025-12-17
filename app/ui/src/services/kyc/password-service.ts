import {resolve} from "@aurelia/kernel";
import {KYCApi} from "./kyc-api";

export interface ForgotPasswordRequest {
    email: string
}

export interface ResetPasswordRequest {
    token: string,
    newPassword: string
}

export interface ChangePasswordRequest {
    password: string
}

export class PasswordService {
    public kycApi = resolve(KYCApi);

    async forgotPassword(request: ForgotPasswordRequest) {
        return await this.kycApi.httpClient.post(`/api/password/forgot`, JSON.stringify(request));
    }

    async resetPassword(request: ResetPasswordRequest) {
        return await this.kycApi.httpClient.post(`/api/password/reset`, JSON.stringify(request));
    }

    async changePassword(request: ChangePasswordRequest) {
        return await this.kycApi.httpClient.post(`/sapi/password/change`, JSON.stringify(request));
    }
}

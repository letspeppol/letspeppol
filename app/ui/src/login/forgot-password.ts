import {resolve} from "@aurelia/kernel";
import {ForgotPasswordRequest, PasswordService} from "../services/kyc/password-service";

export class ForgotPassword {
    readonly passwordService = resolve(PasswordService);
    requestSent = false;
    email;
    error;

    async forgotPassword() {
        const request = {
            email: this.email,
        } as ForgotPasswordRequest;
        try {
            await this.passwordService.forgotPassword(request);
            this.error = false;
            this.requestSent = true;
        } catch (e) {
            this.error = true;
        }
    }
}

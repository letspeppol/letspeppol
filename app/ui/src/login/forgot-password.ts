import {resolve} from "@aurelia/kernel";
import {ForgotPasswordRequest, PasswordService} from "../services/kyc/password-service";

export class ForgotPassword {
    readonly passwordService = resolve(PasswordService);
    requestSent = false;
    sending = false;
    email;
    error;

    async forgotPassword() {
        if (this.sending) return;
        const request = {
            email: this.email,
        } as ForgotPasswordRequest;
        this.sending = true;
        try {
            await this.passwordService.forgotPassword(request);
            this.error = false;
            this.requestSent = true;
        } catch {
            this.error = true;
        } finally {
            this.sending = false;
        }
    }
}

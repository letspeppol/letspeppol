import {Params, RouteNode, Router} from "@aurelia/router";
import {resolve} from "@aurelia/kernel";
import {PasswordService, ResetPasswordRequest} from "../services/kyc/password-service";
import {ChoosePassword} from "../components/choose-password/choose-password";

export class ResetPassword {
    readonly passwordService = resolve(PasswordService);
    readonly router = resolve(Router);
    error: boolean = false;
    token: string;
    password: string;
    confirmPassword: string;
    choosePassword: ChoosePassword;

    public loading(params: Params, next: RouteNode) {
        this.token = next.queryParams.get('token');
    }

    async resetPassword() {
        if (!this.choosePassword?.rules.pwStrong || !this.choosePassword?.rules.matchOk) {
            return;
        }
        const request = {
            token: this.token,
            newPassword: this.password,
        } as ResetPasswordRequest;
        try {
            await this.passwordService.resetPassword(request);
            await this.router.load('login')
        } catch (e) {
            this.error = true;
        }
    }

}

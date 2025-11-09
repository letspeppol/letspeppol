import {Params, RouteNode, Router} from "@aurelia/router";
import {resolve} from "@aurelia/kernel";
import {PasswordService, ResetPasswordRequest} from "../services/kyc/password-service";

export class ResetPassword {
    readonly passwordService = resolve(PasswordService);
    readonly router = resolve(Router);
    error: boolean = false;
    token: string;
    password: string;
    confirmPassword: string;

    public loading(params: Params, next: RouteNode) {
        this.token = next.queryParams.get('token');
    }

    async resetPassword() {
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

import {IRouterEvents, Params, RouteNode, Router} from "@aurelia/router";
import {resolve} from "@aurelia/kernel";
import {PasswordService, ResetPasswordRequest} from "../services/kyc/password-service";
import {ChoosePassword} from "../components/choose-password/choose-password";
import {clearTokenFromUrl} from "../services/util/url";

export class ResetPassword {
    readonly passwordService = resolve(PasswordService);
    readonly router = resolve(Router);
    private readonly routerEvents = resolve(IRouterEvents);
    error: boolean = false;
    saving = false;
    token: string;
    password: string;
    confirmPassword: string;
    choosePassword: ChoosePassword;

    public loading(params: Params, next: RouteNode) {
        this.token = next.queryParams.get('token');
        const navigationEnd = this.routerEvents.subscribe('au:router:navigation-end', () => {
            navigationEnd.dispose();
            clearTokenFromUrl();
        });
    }

    async resetPassword() {
        if (this.saving || !this.choosePassword?.rules.pwStrong || !this.choosePassword?.rules.matchOk) {
            return;
        }
        const request = {
            token: this.token,
            newPassword: this.password,
        } as ResetPasswordRequest;
        this.saving = true;
        this.error = false;
        try {
            await this.passwordService.resetPassword(request);
            await this.router.load('login')
        } catch {
            this.error = true;
        } finally {
            this.saving = false;
        }
    }

}

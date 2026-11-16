import {IRouter, Params, RouteNode} from "@aurelia/router";
import {resolve} from "@aurelia/kernel";
import {I18N} from "@aurelia/i18n";
import {toErrorResponse, toLocalizedErrorMessage} from "../app/util/error-response-handler";
import {ChoosePassword} from "../components/choose-password/choose-password";
import {rememberLoginHint} from "../login/remembered-email";
import {CompanyUserService, InvitationInfo} from "../services/kyc/company-user-service";
import {LoginService} from "../services/app/login-service";
import {ownershipRoute} from "../services/app/ownership-route";
import {OwnershipService} from "../services/app/ownership-service";
import {clearTokenFromUrl} from "../services/util/url";

export class Invitation {
    private readonly companyUserService = resolve(CompanyUserService);
    private readonly loginService = resolve(LoginService);
    private readonly ownershipService = resolve(OwnershipService);
    private readonly router = resolve(IRouter);
    private readonly i18n = resolve(I18N);
    token: string | null = null;
    info: InvitationInfo | undefined = undefined;
    verifying = true;
    submitting = false;
    accepted = false;
    errorText = '';
    password = '';
    confirmPassword = '';
    choosePassword: ChoosePassword;

    public loading(params: Params, next: RouteNode) {
        this.token = next.queryParams.get('token');
        void this.verify();
    }

    async verify() {
        if (!this.token) {
            this.verifying = false;
            this.errorText = this.i18n.tr('error.invitation_not_found');
            return;
        }
        try {
            this.info = await this.companyUserService.verifyInvitation(this.token);
        } catch (e: unknown) {
            this.errorText = toLocalizedErrorMessage(await toErrorResponse(e), this.i18n, this.i18n.tr('invitation.verify-failed'));
        } finally {
            this.verifying = false;
            clearTokenFromUrl();
        }
    }

    async logIn() {
        if (await this.loginService.ensureAuthenticated()
            && this.ownershipService.getCurrentAccountEmail() === this.info.email) {
            void this.router.load(ownershipRoute(this.info.peppolId, '/dashboard'));
            return;
        }
        rememberLoginHint(this.info.email);
        if (this.loginService.authenticated) {
            this.loginService.logout();
            return;
        }
        void this.router.load('/login');
    }

    get canAccept(): boolean {
        if (!this.info || this.submitting) {
            return false;
        }
        if (!this.info.passwordRequired) {
            return true;
        }
        return !!this.password && !!this.choosePassword?.rules.pwStrong && !!this.choosePassword?.rules.matchOk;
    }

    async accept() {
        if (!this.canAccept) {
            return;
        }
        this.submitting = true;
        this.errorText = '';
        try {
            await this.companyUserService.acceptInvitation(
                this.info.passwordRequired ? {token: this.token, newPassword: this.password} : {token: this.token}
            );
            this.accepted = true;
        } catch (e: unknown) {
            this.errorText = toLocalizedErrorMessage(await toErrorResponse(e), this.i18n, this.i18n.tr('invitation.accept-failed'));
        } finally {
            this.submitting = false;
        }
    }
}

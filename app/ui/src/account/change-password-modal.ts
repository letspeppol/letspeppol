import {resolve} from "@aurelia/kernel";
import {ChangePasswordRequest, PasswordService} from "../services/kyc/password-service";
import {AlertType} from "../components/alert/alert";
import {computed, IEventAggregator} from "aurelia";
import {I18N} from "@aurelia/i18n";

export class ChangePasswordModal {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly passwordService = resolve(PasswordService);
    private readonly i18n = resolve(I18N);
    open: boolean = false;
    error: boolean = false;
    password: string;
    confirmPassword: string;

    public showChangePasswordModal() {
        this.password = '';
        this.confirmPassword = '';
        this.open = true;
    }

    async changePassword() {
        const request = {
            password: this.password,
        } as ChangePasswordRequest;
        try {
            await this.passwordService.changePassword(request);
            this.error = false;
            this.open = false;
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('alert.password.changed')});
        } catch (e) {
            this.error = true;
        }
    }

    @computed({ deps: ['passwordService.kycApi.httpClient.isRequesting'] })
    get isRequesting() {
        return this.passwordService.kycApi.httpClient.isRequesting;
    }

    closeModal() {
        this.open = false;
    }
}
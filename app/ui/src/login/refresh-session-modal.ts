import {resolve} from "@aurelia/kernel";
import {LoginService} from "../services/app/login-service";
import {watch} from "aurelia";

export class RefreshSessionModal {
    private readonly loginService = resolve(LoginService);
    private timeout?: number | undefined;

    created() {
        this.scheduleRefresh();
    }

    @watch((vm) => vm.loginService.authenticated)
    onAuthChanged() {
        this.scheduleRefresh();
    }

    private scheduleRefresh() {
        this.clearTimer();
        if (!this.loginService.authenticated) return;

        const token = localStorage.getItem('token');
        if (!token) return;

        const expiryDate = this.loginService.getTokenExpiryDateInSeconds(token);
        const currentDate = this.loginService.getCurrentDateInSeconds();
        const secondsUntilExpiry = expiryDate - currentDate;

        if (secondsUntilExpiry <= 0) return;

        // Refresh 5 minutes before expiry
        const refreshInMs = Math.max((secondsUntilExpiry - 300) * 1000, 0);
        this.timeout = window.setTimeout(() => this.attemptRefresh(), refreshInMs);
    }

    private async attemptRefresh() {
        const success = await this.loginService.refreshToken();
        if (success) {
            this.scheduleRefresh();
        } else {
            this.loginService.logout();
        }
    }

    private clearTimer() {
        if (this.timeout) {
            clearTimeout(this.timeout);
            this.timeout = undefined;
        }
    }
}

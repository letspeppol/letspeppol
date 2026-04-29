import {resolve} from "@aurelia/kernel";
import {LoginService} from "../services/app/login-service";
import {watch} from "aurelia";

const REFRESH_LOCK_KEY = 'token_refresh_lock';
const LOCK_TTL_MS = 10_000;

export class RefreshSessionModal {
    private readonly loginService = resolve(LoginService);
    private timeout?: number | undefined;
    private onStorageChange = (e: StorageEvent) => this.handleStorageEvent(e);

    created() {
        window.addEventListener('storage', this.onStorageChange);
        this.scheduleRefresh();
    }

    detaching() {
        window.removeEventListener('storage', this.onStorageChange);
        this.clearTimer();
    }

    @watch((vm) => vm.loginService.authenticated)
    onAuthChanged() {
        this.scheduleRefresh();
    }

    private handleStorageEvent(e: StorageEvent) {
        if (e.key === 'token') {
            // Another tab refreshed the token — reschedule with the new expiry
            this.loginService.verifyAuthenticated();
            this.scheduleRefresh();
        }
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

    private acquireLock(): boolean {
        const now = Date.now();
        const existing = localStorage.getItem(REFRESH_LOCK_KEY);
        if (existing && (now - parseInt(existing, 10)) < LOCK_TTL_MS) {
            return false; // Another tab is already refreshing
        }
        localStorage.setItem(REFRESH_LOCK_KEY, now.toString());
        return true;
    }

    private releaseLock() {
        localStorage.removeItem(REFRESH_LOCK_KEY);
    }

    private async attemptRefresh() {
        if (!this.acquireLock()) {
            // Another tab is handling the refresh — reschedule to pick up the new token
            this.scheduleRefresh();
            return;
        }

        try {
            const success = await this.loginService.refreshToken();
            if (success) {
                this.scheduleRefresh();
            } else {
                this.loginService.logout();
            }
        } finally {
            this.releaseLock();
        }
    }

    private clearTimer() {
        if (this.timeout) {
            clearTimeout(this.timeout);
            this.timeout = undefined;
        }
    }
}

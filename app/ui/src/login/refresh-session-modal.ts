import {resolve} from "@aurelia/kernel";
import {LoginService} from "../services/app/login-service";
import {watch} from "aurelia";

export class RefreshSessionModal {
    private readonly loginService = resolve(LoginService);
    private timeout?: number | undefined;
    private email;
    private password;
    private open = false;
    private error = false;

    created() {
        this.verifyAuthenticated();
    }

    @watch((vm) => vm.loginService.authenticated)
    startTimer() {
        this.verifyAuthenticated();
    }

    verifyAuthenticated() {
        if (this.loginService.authenticated) {
            this.clearTimer();
        } else {
            this.clearTimer();
        }
    }

    clearTimer() {
        if (this.timeout) {
            clearTimeout(this.timeout);
        }
    }

    setTimer() {
        let timeoutInMillis = 55 * 60 * 1000;
        const token = localStorage.getItem('token');
        if (token) {
            const expiryDate = this.loginService.getTokenExpiryDateInSeconds(token);
            const currentDate = this.loginService.getCurrentDateInSeconds();
            if (expiryDate > currentDate && ((expiryDate - currentDate) < 300)) {
                timeoutInMillis = ((expiryDate - currentDate) - 300) * 1000;
            }
        }
        setTimeout(() => this.showModal(), timeoutInMillis);
    }

    showModal() {
        if (this.open === true) {
            return;
        }
        this.error = false;
        const email = localStorage.getItem("email");
        if (email) {
            this.email = email;
        }
        this.open = true;
    }

    async verifyLogin() {
        try {
            await this.loginService.auth(this.email, this.password);
            this.open = false;
            this.setTimer();
        } catch {
            this.error = true;
        }
    }
}

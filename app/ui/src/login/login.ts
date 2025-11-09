import {resolve} from "@aurelia/kernel";
import {LoginService} from "../services/app/login-service";
import {IRouter} from "@aurelia/router";
import {CompanyService} from "../services/app/company-service";

export class Login {
    private readonly loginService = resolve(LoginService);
    private readonly companyService = resolve(CompanyService);
    private readonly router: IRouter = resolve(IRouter);
    email: string;
    password: string;
    error: boolean = false;
    rememberMe: boolean = false;

    attached() {
        const email = localStorage.getItem("email")
        if (email) {
            this.rememberMe = true;
            this.email = email;
        }
        this.verifyAuthenticated();

    }

    async verifyLogin() {
        try {
            await this.loginService.auth(this.email, this.password);
            await this.loginSuccess();
        } catch {
            this.error = true;
        }
    }

    async loginSuccess() {
        await this.companyService.getAndSetMyCompanyForToken();
        this.error = false;
        if (this.rememberMe) {
            localStorage.setItem('email', this.email);
        }
        await this.router.load('invoices');
    }

    verifyAuthenticated() {
        if (this.loginService.authenticated) {
            this.loginSuccess().catch(() => {
                this.loginService.logout();
                this.error = true;
            });
        }
    }
}

import {resolve} from "@aurelia/kernel";
import {LoginService} from "../services/login-service";
import {IRouter} from "@aurelia/router";
import {CompanyService} from "../services/company-service";

export class Login {
    private readonly loginService = resolve(LoginService);
    private readonly companyService = resolve(CompanyService);
    private readonly router: IRouter = resolve(IRouter);
    email: string;
    password: string;
    error: boolean = false;

    attached() {
        this.verifyAuthenticated();
    }

    async check() {
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

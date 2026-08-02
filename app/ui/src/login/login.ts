import {resolve} from "@aurelia/kernel";
import {LoginService} from "../services/app/login-service";
import {IRouter} from "@aurelia/router";
import {CompanyService} from "../services/app/company-service";
import {IEventAggregator} from "aurelia";
import {SHOW_WELCOME_NOTIFICATIONS} from "../components/welcome-notification/welcome-notification-modal";

export class Login {
    private readonly loginService = resolve(LoginService);
    private readonly companyService = resolve(CompanyService);
    private readonly router: IRouter = resolve(IRouter);
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    email: string;
    password: string;
    error: boolean = false;
    rememberMe: boolean = false;

    attached() {
        const email = localStorage.getItem("email");
        if (email) {
            this.rememberMe = true;
            this.email = email;
        }
        //this.verifyAuthenticated(); // TODO
        this.loginService.logout(); // TODO
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
        await this.companyService.getAndSetMyCompanyForToken().then(result => localStorage.setItem('peppolActive', String(result.peppolActive)));
        this.error = false;
        if (this.rememberMe) {
            localStorage.setItem('email', this.email);
        }
        await this.router.load('dashboard');
        this.ea.publish(SHOW_WELCOME_NOTIFICATIONS);
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

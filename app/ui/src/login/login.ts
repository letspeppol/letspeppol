import {resolve} from "@aurelia/kernel";
import {LoginService} from "../services/app/login-service";

export class Login {
    private readonly loginService = resolve(LoginService);

    attached() {
        if (this.loginService.authenticated) {
            return;
        }
        this.loginService.initiateLogin();
    }
}

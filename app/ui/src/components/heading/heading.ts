import {resolve} from "@aurelia/kernel";
import {Router} from "@aurelia/router";
import {ThemeService} from '../../services/app/theme-service';
import {LoginService} from "../../services/app/login-service";

export class Heading {
    private loginService = resolve(LoginService);
    private router = resolve(Router);
    private theme = resolve(ThemeService);

    attached() {
    }

    logout() {
        this.loginService.logout();
        this.router.load('login');
    }

}

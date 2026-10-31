import {bindable} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {IRouter} from "@aurelia/router";
import {LoginService} from "../services/app/login-service";
import {OwnershipService} from "../services/app/ownership-service";

export class SettingsTabs {
    private readonly loginService = resolve(LoginService);
    private readonly router = resolve(IRouter);
    readonly ownershipService = resolve(OwnershipService);
    @bindable active: 'company' | 'users' = 'company';

    async open(tab: 'company' | 'users') {
        if (tab !== this.active) {
            await this.router.load(this.loginService.getCurrentOwnershipRoute(tab === 'users' ? '/settings/users' : '/settings'));
        }
    }
}

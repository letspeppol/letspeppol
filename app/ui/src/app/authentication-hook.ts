import {lifecycleHooks} from '@aurelia/runtime-html';
import {IRouteViewModel, NavigationInstruction, Params, RouteNode} from '@aurelia/router';
import {LoginService} from "../services/app/login-service";
import {resolve} from "@aurelia/kernel";

@lifecycleHooks()
export class AuthenticationHook {
    loginService = resolve(LoginService);

    async canLoad(viewModel: IRouteViewModel, params: Params, next: RouteNode): Promise<boolean | NavigationInstruction> {
        if (next.data?.allowEveryone) {
            return true;
        }
        if (!this.loginService.authenticated) {
            return 'login';
        }
        return true;
    }
}

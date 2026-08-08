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
        if (this.loginService.authenticated) {
            return true;
        }
        // Not authenticated in this tab — try to restore the session silently against the KYC
        // session cookie before falling back to the interactive login route.
        const restored = await this.loginService.ensureAuthenticated();
        return restored ? true : 'login';
    }
}

import {lifecycleHooks} from '@aurelia/runtime-html';
import {IRouteViewModel, NavigationInstruction, Params, RouteNode} from '@aurelia/router';
import {LoginService} from "../services/app/login-service";
import {OwnershipService} from "../services/app/ownership-service";
import {resolve} from "@aurelia/kernel";

@lifecycleHooks()
export class AuthenticationHook {
    loginService = resolve(LoginService);
    ownershipService = resolve(OwnershipService);

    async canLoad(viewModel: IRouteViewModel, params: Params, next: RouteNode): Promise<boolean | NavigationInstruction> {
        if (next.data?.allowEveryone) {
            return true;
        }
        // Not authenticated in this tab — try to restore the session silently against the KYC
        // session cookie before falling back to the interactive login route.
        const requestedPeppolId = typeof params.peppolId === 'string' ? params.peppolId : undefined;
        const restored = await this.loginService.ensureAuthenticated(requestedPeppolId);
        if (!restored) {
            this.loginService.rememberCurrentNavigation();
            return '/login';
        }
        const requiredPermission = next.data?.permission;
        if (typeof requiredPermission === 'string') {
            return this.ownershipService.hasPermission(requiredPermission) ? true : this.loginService.getHomeRoute();
        }
        if (requestedPeppolId) {
            return true;
        }
        return this.loginService.getHomeRoute();
    }
}

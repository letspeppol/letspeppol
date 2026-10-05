import {lifecycleHooks} from '@aurelia/runtime-html';
import {IRouteViewModel, NavigationInstruction, Params, RouteNode} from '@aurelia/router';
import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {I18N} from "@aurelia/i18n";
import {AlertType} from "../components/alert/alert";
import {CompanyPermission} from "../services/app/company-permission";
import {ownershipRoute} from "../services/app/ownership-route";
import {OwnershipService} from "../services/app/ownership-service";

export interface CompanyRouteData {
    allowEveryone?: boolean;
    adminOnly?: boolean;
    requiresAny?: CompanyPermission[];
}

@lifecycleHooks()
export class CompanyPermissionHook {
    private readonly ownershipService = resolve(OwnershipService);
    private readonly ea = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);

    canLoad(viewModel: IRouteViewModel, params: Params, next: RouteNode): boolean | NavigationInstruction {
        const data = (next.data ?? {}) as CompanyRouteData;
        if (data.allowEveryone || this.isAllowed(data)) {
            return true;
        }
        this.ea.publish('alert', {alertType: AlertType.Warning, text: this.i18n.tr('alert.permission.route-denied')});
        const peppolId = typeof params.peppolId === 'string' ? params.peppolId : this.ownershipService.getCurrentPeppolId();
        return peppolId ? ownershipRoute(peppolId, '/dashboard') : '/dashboard';
    }

    private isAllowed(data: CompanyRouteData): boolean {
        if (data.adminOnly && !this.ownershipService.admin) {
            return false;
        }
        if (data.requiresAny?.length) {
            return data.requiresAny.some(permission => this.ownershipService.can(permission));
        }
        return true;
    }
}

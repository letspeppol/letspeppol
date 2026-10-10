import {resolve} from "@aurelia/kernel";
import {IRouter, ICurrentRoute} from "@aurelia/router";
import {IEventAggregator, IDisposable} from 'aurelia';
import {LoginService} from "../../services/app/login-service";
import {OwnershipService} from "../../services/app/ownership-service";
import {currentOwnershipRoute} from "../../services/app/ownership-route";

export class ApplicationView {
    private readonly router: IRouter = resolve(IRouter);
    private readonly currentRoute = resolve(ICurrentRoute);
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly loginService = resolve(LoginService);
    private readonly ownershipService = resolve(OwnershipService);
    private readonly subscriptions: IDisposable[] = [];
    peppolActive: boolean = true;

    async attached() {
        this.subscriptions.push(this.ea.subscribe('account:switched', () => {
            void this.refreshPeppolActive();
        }));
        this.subscriptions.push(this.ea.subscribe('account:peppol-status-changed', (peppolActive: boolean) => {
            this.peppolActive = peppolActive;
        }));
        await this.refreshPeppolActive();
    }

    unbinding() {
        for (const subscription of this.subscriptions.splice(0)) {
            subscription.dispose();
        }
    }

    async refreshPeppolActive() {
        if (!await this.loginService.ensureAuthenticated()) {
            return;
        }

        const ownerships = await this.ownershipService.loadOwnerships();
        const currentOwnershipKey = this.ownershipService.getCurrentOwnershipKey();
        const currentOwnership = ownerships.find(ownership =>
            this.ownershipService.getOwnershipKey(ownership.peppolId, ownership.type) === currentOwnershipKey
        );

        if (currentOwnership) {
            this.peppolActive = currentOwnership.peppolActive;
            localStorage.setItem('peppolActive', String(currentOwnership.peppolActive));
        }
    }

    goHome() {
        this.router.load(currentOwnershipRoute('/dashboard'));
    }

    notPeppolActiveAction() {
        if (this.currentRoute.path.endsWith('/account')) {
            this.ea.publish('account:register');
            return;
        }
        history.replaceState({ ...(history.state ?? {}), runRegister: true }, '');
        this.router.load(currentOwnershipRoute('/account'));
    }
}

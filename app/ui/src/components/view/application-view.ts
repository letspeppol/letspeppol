import {resolve} from "@aurelia/kernel";
import {IRouter, ICurrentRoute} from "@aurelia/router";
import {IEventAggregator, IDisposable} from 'aurelia';

export class ApplicationView {
    private readonly router: IRouter = resolve(IRouter);
    private readonly currentRoute = resolve(ICurrentRoute);
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private sub?: IDisposable;
    peppolActive: boolean = true;

    attached() {
        this.refreshPeppolActive();
        this.sub = this.ea.subscribe('account:switched', () => {
            this.refreshPeppolActive();
        });
    }

    unbinding() {
        this.sub?.dispose();
    }

    refreshPeppolActive() {
        this.peppolActive = localStorage.getItem('peppolActive') === 'true';
    }

    goHome() {
        this.router.load('/');
    }

    notPeppolActiveAction() {
        if (this.currentRoute.path.startsWith('account')) {
            this.ea.publish('account:register');
            return;
        }
        history.replaceState({ ...(history.state ?? {}), runRegister: true }, '');
        this.router.load('/account');
    }
}

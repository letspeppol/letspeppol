import {resolve} from "@aurelia/kernel";
import {IRouter} from "@aurelia/router";
import {ThemeService} from '../../services/app/theme-service';
import {currentOwnershipRoute} from '../../services/app/ownership-route';

export class WizardView {
    private readonly router: IRouter = resolve(IRouter);
    private theme = resolve(ThemeService);

    attached() {
    }

    goHome() {
        this.router.load(currentOwnershipRoute('/dashboard'));
    }
}

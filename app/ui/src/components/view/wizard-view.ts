import {bindable} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {IRouter} from "@aurelia/router";
import {currentOwnershipRoute} from '../../services/app/ownership-route';

export class WizardView {
    @bindable wide = false;
    private readonly router: IRouter = resolve(IRouter);

    goHome() {
        this.router.load(currentOwnershipRoute('/dashboard'));
    }
}

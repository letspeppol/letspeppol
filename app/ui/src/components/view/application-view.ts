import {resolve} from "@aurelia/kernel";
import {IRouter} from "@aurelia/router";

export class ApplicationView {
    private readonly router: IRouter = resolve(IRouter);

    attached() {
    }

    goHome() {
        this.router.load('/');
    }
}

import {IDisposable, IEventAggregator} from "aurelia";
import {resolve} from "@aurelia/kernel";

export class Overlay {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private showOverlaySubscription: IDisposable;
    private hideOverlaySubscription: IDisposable;
    private show = false;
    private text = "";

    bound() {
        this.showOverlaySubscription = this.ea.subscribe('showOverlay', (text: string) => this.showOverlay(text));
        this.hideOverlaySubscription = this.ea.subscribe('hideOverlay', () => this.hideOverlay());
    }

    unbinding() {
        if (this.showOverlaySubscription) this.showOverlaySubscription.dispose();
        if (this.hideOverlaySubscription) this.hideOverlaySubscription.dispose();
    }

    showOverlay(text: string) {
        this.text = text;
        this.show = true;
    }

    hideOverlay() {
        this.show = false;
        this.text = '';
    }

}

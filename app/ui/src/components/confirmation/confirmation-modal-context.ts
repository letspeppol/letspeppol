import {singleton} from "aurelia";

@singleton
export class ConfirmationModalContext {
    title: string;
    text: string;
    open: boolean = false;
    variant: 'default' | 'warning' = 'default';
    yesFunction = () => { }
    noFunction = () => { }

    showConfirmationModal(title: string, text: string, yesFunction: () => void, noFunction: () => void, variant: 'default' | 'warning' = 'default') {
        this.title = title;
        this.text = text;
        this.yesFunction = yesFunction;
        this.noFunction = noFunction;
        this.variant = variant;
        this.open = true;
    }
}

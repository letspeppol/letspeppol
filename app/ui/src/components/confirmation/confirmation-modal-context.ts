import {singleton} from "aurelia";

export interface ConfirmationOptions {
    confirmLabel?: string;
    cancelLabel?: string;
    destructive?: boolean;
}

@singleton
export class ConfirmationModalContext {
    title: string;
    text: string;
    open: boolean = false;
    variant: 'default' | 'warning' = 'default';
    confirmLabel?: string;
    cancelLabel?: string;
    destructive = false;
    yesFunction = () => { }
    noFunction = () => { }

    showConfirmationModal(title: string, text: string, yesFunction: () => void, noFunction: () => void, variant: 'default' | 'warning' = 'default', options: ConfirmationOptions = {}) {
        this.title = title;
        this.text = text;
        this.yesFunction = yesFunction;
        this.noFunction = noFunction;
        this.variant = variant;
        this.confirmLabel = options.confirmLabel;
        this.cancelLabel = options.cancelLabel;
        this.destructive = options.destructive ?? false;
        this.open = true;
    }
}

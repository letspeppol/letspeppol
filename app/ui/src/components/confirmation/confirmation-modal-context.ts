import {singleton} from "aurelia";

@singleton
export class ConfirmationModalContext {
    title: string;
    text: string;
    open: boolean = false;
    yesFunction = () => { }
    noFunction = () => { }

    showConfirmationModal(title: string, text: string, yesFunction: () => void, noFunction: () => void) {
        this.title = title;
        this.text = text;
        this.yesFunction = yesFunction;
        this.noFunction = noFunction;
        this.open = true;
    }
}
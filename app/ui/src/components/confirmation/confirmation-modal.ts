import {resolve} from "@aurelia/kernel";
import {ConfirmationModalContext} from "./confirmation-modal-context";
import {onModalEnter} from "../util/modal-keyboard";

export class ConfirmationModal {
    private readonly confirmationContext = resolve(ConfirmationModalContext);

    yes() {
        if (this.confirmationContext.yesFunction) {
            this.confirmationContext.yesFunction();
        }
        this.confirmationContext.open = false;
    }

    no() {
        if (this.confirmationContext.noFunction) {
            this.confirmationContext.noFunction();
        }
        this.confirmationContext.open = false;
    }

    onKeyDown(event: KeyboardEvent) {
        onModalEnter(event, () => this.yes());
    }
}

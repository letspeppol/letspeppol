import {resolve} from "@aurelia/kernel";
import {ConfirmationModalContext} from "./confirmation-modal-context";

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
        if (event.key !== 'Enter' || this.shouldIgnoreEnter(event.target)) {
            return;
        }
        event.preventDefault();
        this.yes();
    }

    private shouldIgnoreEnter(target: EventTarget | null) {
        const element = target as HTMLElement | null;
        if (!element) {
            return false;
        }
        return ['BUTTON', 'A', 'TEXTAREA'].includes(element.tagName);
    }
}

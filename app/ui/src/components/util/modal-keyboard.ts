/**
 * Returns true when pressing Enter on the given target should NOT trigger a modal's
 * primary action (e.g. when focus is on a button, link or textarea).
 */
export function shouldIgnoreEnter(target: EventTarget | null): boolean {
    const element = target as HTMLElement | null;
    if (!element) {
        return false;
    }
    return ['BUTTON', 'A', 'TEXTAREA'].includes(element.tagName);
}

/**
 * Runs the given action when Enter is pressed on a modal, unless focus is on an element
 * where Enter has its own meaning. Prevents the default form submission/newline behaviour.
 */
export function onModalEnter(event: KeyboardEvent, action: () => void): void {
    if (event.key !== 'Enter' || shouldIgnoreEnter(event.target)) {
        return;
    }
    event.preventDefault();
    action();
}

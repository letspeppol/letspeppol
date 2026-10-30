const FOCUSABLE = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled], [type="hidden"])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
].join(',');

export function focusableElements(container: HTMLElement): HTMLElement[] {
    return [...container.querySelectorAll<HTMLElement>(FOCUSABLE)]
        .filter(element => element.getClientRects().length > 0);
}

export function focusFirst(container: HTMLElement | undefined): void {
    if (!container) {
        return;
    }
    (focusableElements(container)[0] ?? container).focus();
}

export function keepFocusInside(event: KeyboardEvent, container: HTMLElement | undefined): void {
    if (event.key !== 'Tab' || !container) {
        return;
    }
    const elements = focusableElements(container);
    if (elements.length === 0) {
        event.preventDefault();
        return;
    }
    const first = elements[0];
    const last = elements[elements.length - 1];
    const active = document.activeElement;
    if (event.shiftKey && (active === first || !container.contains(active))) {
        event.preventDefault();
        last.focus();
    } else if (!event.shiftKey && (active === last || !container.contains(active))) {
        event.preventDefault();
        first.focus();
    }
}

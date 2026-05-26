import { DI, Registration } from '@aurelia/kernel';

export type VatDisplayMode = 'incl' | 'excl';

export type VatDisplayListener = (mode: VatDisplayMode) => void;

export interface IVatDisplay {
    mode: VatDisplayMode;
    setMode(mode: VatDisplayMode): void;
    subscribe(fn: VatDisplayListener): () => void;
}

export const IVatDisplay = DI.createInterface<IVatDisplay>('IVatDisplay');

const STORAGE_KEY = 'lp.vatDisplay.v1';

export class LocalStorageVatDisplay implements IVatDisplay {
    mode: VatDisplayMode = this.load();
    private listeners = new Set<VatDisplayListener>();

    constructor() {
        if (typeof window !== 'undefined') {
            window.addEventListener('storage', (e) => {
                if (e.key !== STORAGE_KEY) return;
                const next: VatDisplayMode = e.newValue === 'incl' ? 'incl' : 'excl';
                if (this.mode === next) return;
                this.mode = next;
                this.notify(next);
            });
        }
    }

    setMode(mode: VatDisplayMode): void {
        if (mode !== 'incl' && mode !== 'excl') return;
        if (this.mode === mode) return;
        this.mode = mode;
        try { localStorage.setItem(STORAGE_KEY, mode); } catch { /* quota / private mode */ }
        this.notify(mode);
    }

    subscribe(fn: VatDisplayListener): () => void {
        this.listeners.add(fn);
        return () => { this.listeners.delete(fn); };
    }

    private notify(mode: VatDisplayMode): void {
        for (const fn of this.listeners) {
            try { fn(mode); } catch { /* listener errors must not break the chain */ }
        }
    }

    private load(): VatDisplayMode {
        // Default 'excl' matches pre-feature behaviour so existing users see the same numbers.
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            return raw === 'incl' ? 'incl' : 'excl';
        } catch {
            return 'excl';
        }
    }
}

export const VatDisplayRegistration = Registration.singleton(IVatDisplay, LocalStorageVatDisplay);

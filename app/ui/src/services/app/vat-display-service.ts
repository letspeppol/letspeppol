import { DI, Registration } from '@aurelia/kernel';

export type VatDisplayMode = 'incl' | 'excl';

export type VatDisplayListener = (mode: VatDisplayMode) => void;

export interface IVatDisplay {
    mode: VatDisplayMode;
    setMode(mode: VatDisplayMode): void;
    useAccount(accountId: string | null): void;
    subscribe(fn: VatDisplayListener): () => void;
}

export const IVatDisplay = DI.createInterface<IVatDisplay>('IVatDisplay');

export function hasVatNumber(vatNumber?: string): boolean {
    return !!vatNumber?.trim();
}

export function getVatDisplayMode(
    vatNumber: string | undefined,
    preferredMode: VatDisplayMode = 'excl',
): VatDisplayMode {
    return hasVatNumber(vatNumber) ? preferredMode : 'incl';
}

export function getAutomaticVatDisplayMode(vatNumber?: string): VatDisplayMode {
    return getVatDisplayMode(vatNumber, 'excl');
}

const STORAGE_KEY = 'lp.vatDisplay.v1';

export class LocalStorageVatDisplay implements IVatDisplay {
    private accountId: string | null = null;
    mode: VatDisplayMode = this.load();
    private listeners = new Set<VatDisplayListener>();

    constructor() {
        if (typeof window !== 'undefined') {
            window.addEventListener('storage', (e) => {
                if (e.key !== this.storageKey) return;
                this.apply(e.newValue === 'incl' ? 'incl' : 'excl');
            });
        }
    }

    useAccount(accountId: string | null): void {
        if (this.accountId === accountId) return;
        this.accountId = accountId;
        this.apply(this.load());
    }

    setMode(mode: VatDisplayMode): void {
        if (mode !== 'incl' && mode !== 'excl') return;
        if (this.mode === mode) return;
        this.mode = mode;
        try { localStorage.setItem(this.storageKey, mode); } catch { /* quota / private mode */ }
        this.notify(mode);
    }

    private get storageKey(): string {
        return this.accountId ? `${STORAGE_KEY}:${this.accountId}` : STORAGE_KEY;
    }

    private apply(mode: VatDisplayMode): void {
        if (this.mode === mode) return;
        this.mode = mode;
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
            const raw = localStorage.getItem(this.storageKey) ?? localStorage.getItem(STORAGE_KEY);
            return raw === 'incl' ? 'incl' : 'excl';
        } catch {
            return 'excl';
        }
    }
}

export const VatDisplayRegistration = Registration.singleton(IVatDisplay, LocalStorageVatDisplay);

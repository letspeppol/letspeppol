import { DI, Registration } from '@aurelia/kernel';
import { featuresInSection, isExpired } from './feature-registry';

export type FeatureAckListener = (id: string) => void;

export interface IFeatureAck {
    isAcknowledged(id: string): boolean;
    acknowledge(id: string): void;
    hasUnacknowledgedInSection(section: string): boolean;
    subscribe(fn: FeatureAckListener): () => void;
}

export const IFeatureAck = DI.createInterface<IFeatureAck>('IFeatureAck');

const STORAGE_KEY = 'lp.featureAck.v1';

export class LocalStorageFeatureAck implements IFeatureAck {
    private acknowledged: Set<string> = this.load();
    private listeners = new Set<FeatureAckListener>();

    isAcknowledged(id: string): boolean {
        return this.acknowledged.has(id);
    }

    acknowledge(id: string): void {
        if (this.acknowledged.has(id)) return;
        this.acknowledged.add(id);
        this.save();
        for (const fn of this.listeners) {
            try { fn(id); } catch { /* listener errors must not break the chain */ }
        }
    }

    hasUnacknowledgedInSection(section: string): boolean {
        const ids = featuresInSection(section);
        for (const id of ids) {
            if (!this.acknowledged.has(id) && !isExpired(id)) return true;
        }
        return false;
    }

    subscribe(fn: FeatureAckListener): () => void {
        this.listeners.add(fn);
        return () => { this.listeners.delete(fn); };
    }

    private load(): Set<string> {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            if (!raw) return new Set();
            const parsed = JSON.parse(raw);
            return Array.isArray(parsed) ? new Set(parsed.filter(x => typeof x === 'string')) : new Set();
        } catch {
            return new Set();
        }
    }

    private save(): void {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify([...this.acknowledged]));
        } catch { /* quota / private mode — degrade silently */ }
    }
}

export const FeatureAckRegistration = Registration.singleton(IFeatureAck, LocalStorageFeatureAck);

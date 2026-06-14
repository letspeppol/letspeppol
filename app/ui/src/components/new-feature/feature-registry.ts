export interface FeatureMetadata {
    section?: string;
    expiresAt?: string;
    preservePosition?: boolean;
}

export const FEATURE_REGISTRY: Readonly<Record<string, FeatureMetadata>> = {
    'account-notifications': { section: 'account',  expiresAt: '2026-10-26' },
    'vat-display':           { section: 'account',  expiresAt: '2026-11-26' },
    'donation-bar': { expiresAt: '2026-10-26', preservePosition: true },
    'payment-state-action': { section: 'invoice', expiresAt: '2026-10-26' },
};

export function getFeature(id: string): FeatureMetadata | undefined {
    return FEATURE_REGISTRY[id];
}

export function isExpired(id: string, now: Date = new Date()): boolean {
    const expiresAt = FEATURE_REGISTRY[id]?.expiresAt;
    if (!expiresAt) return false;
    const t = Date.parse(expiresAt);
    return Number.isFinite(t) && t <= now.getTime();
}

export function featuresInSection(section: string): string[] {
    return Object.keys(FEATURE_REGISTRY).filter(id => FEATURE_REGISTRY[id].section === section);
}

export interface FeatureMetadata {
    section?: string;
    expiresAt?: string;
}

export const FEATURE_REGISTRY: Readonly<Record<string, FeatureMetadata>> = {
    'v2.4:account-notifications': { section: 'account',  expiresAt: '2026-10-26' },
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

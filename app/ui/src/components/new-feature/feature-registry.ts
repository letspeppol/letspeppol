export interface FeatureMetadata {
    section?: string;
    expiresAt?: string;
    preservePosition?: boolean;
}

export const FEATURE_REGISTRY: Readonly<Record<string, FeatureMetadata>> = {
    'account-notifications':            { section: 'account',  expiresAt: '2026-10-01' },
    'account-notifications-outgoing':   { section: 'account',  expiresAt: '2026-12-01' },
    'vat-display':                      { section: 'account',  expiresAt: '2026-11-01' },
    'passkeys':                         { section: 'account',  expiresAt: '2026-12-01' },
    'totp':                             { section: 'account',  expiresAt: '2026-12-01' },
    'download-form':                    { section: 'downloads', expiresAt: '2026-12-01' },
    'donation-bar':                     { expiresAt: '2026-10-01', preservePosition: true },
    'add-account':                     { expiresAt: '2026-12-01' },
    'payment-state-action':             { section: 'invoices', expiresAt: '2026-10-01' },
    'allowance-charge':                 { section: 'invoices', expiresAt: '2026-12-01' },
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

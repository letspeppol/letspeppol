const CONTEXT_SECTIONS = new Set([
    'dashboard',
    'invoices',
    'partners',
    'products',
    'sponsors',
    'account',
    'add-ownership',
]);

const PENDING_NAVIGATION_KEY = 'peppol.pending-navigation.v1';
const ACTING_OWNERSHIP_KEY = 'peppol.acting-ownership.v1';

interface RememberedOwnership {
    peppolId: string;
    type: string;
}

export function getPeppolIdFromPath(pathname: string): string | null {
    const segments = pathname.split('/').filter(Boolean);
    if (segments.length < 2 || !CONTEXT_SECTIONS.has(segments[1])) {
        return null;
    }
    try {
        const peppolId = decodeURIComponent(segments[0]);
        return peppolId && peppolId.length <= 32 ? peppolId : null;
    } catch {
        return null;
    }
}

export function ownershipRoute(peppolId: string, target = '/dashboard'): string {
    const encodedPeppolId = encodeURIComponent(peppolId).replace(/%3A/gi, ':');
    const normalizedTarget = `/${target.replace(/^\/+/, '')}`;
    return `/${encodedPeppolId}${normalizedTarget}`;
}

export function currentOwnershipRoute(target: string): string {
    const peppolId = getPeppolIdFromPath(window.location.pathname);
    return peppolId ? ownershipRoute(peppolId, target) : target;
}

export function rememberCurrentNavigation(): void {
    const relativeUrl = `${window.location.pathname}${window.location.search}${window.location.hash}`;
    if (!relativeUrl.startsWith('//')) {
        sessionStorage.setItem(PENDING_NAVIGATION_KEY, relativeUrl);
    }
}

export function peekPendingNavigation(): string | null {
    return safeRelativeNavigation(sessionStorage.getItem(PENDING_NAVIGATION_KEY));
}

export function consumePendingNavigation(): string | null {
    const navigation = peekPendingNavigation();
    sessionStorage.removeItem(PENDING_NAVIGATION_KEY);
    return navigation;
}

export function rememberActingOwnership(peppolId: string, type: string): void {
    sessionStorage.setItem(ACTING_OWNERSHIP_KEY, JSON.stringify({peppolId, type}));
}

export function getRememberedOwnershipType(peppolId: string): string | null {
    const remembered = readRememberedOwnership();
    return remembered?.peppolId === peppolId ? remembered.type : null;
}

export function clearRememberedOwnership(): void {
    sessionStorage.removeItem(ACTING_OWNERSHIP_KEY);
}

function readRememberedOwnership(): RememberedOwnership | null {
    const serialized = sessionStorage.getItem(ACTING_OWNERSHIP_KEY);
    if (!serialized) return null;
    try {
        const parsed = JSON.parse(serialized) as Partial<RememberedOwnership>;
        if (typeof parsed.peppolId === 'string' && typeof parsed.type === 'string') {
            return {peppolId: parsed.peppolId, type: parsed.type};
        }
    } catch {
        // Ignore corrupt per-tab state and fall back to the server-side default.
    }
    sessionStorage.removeItem(ACTING_OWNERSHIP_KEY);
    return null;
}

function safeRelativeNavigation(value: string | null): string | null {
    if (!value || !value.startsWith('/') || value.startsWith('//')) {
        return null;
    }
    try {
        const parsed = new URL(value, window.location.origin);
        if (parsed.origin !== window.location.origin) return null;
        return `${parsed.pathname}${parsed.search}${parsed.hash}`;
    } catch {
        return null;
    }
}

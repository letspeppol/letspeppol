/**
 * Removes a one-time `token` query parameter from the current browser URL (both the standard query
 * string and hash-based routing) without triggering navigation, so single-use auth/reset tokens do
 * not linger in browser history or get shared via copied URLs. Combined with the Referrer-Policy
 * response header this limits token leakage. Best-effort; never throws.
 */
export function clearTokenFromUrl(): void {
    try {
        const url = new URL(window.location.href);
        let changed = false;
        if (url.searchParams.has('token')) {
            url.searchParams.delete('token');
            changed = true;
        }
        if (url.hash.includes('token=')) {
            url.hash = url.hash.replace(/([?&])token=[^&]*/, '$1').replace(/[?&]$/, '');
            changed = true;
        }
        if (changed) {
            window.history.replaceState(window.history.state, '', url.toString());
        }
    } catch {
        /* best-effort, ignore */
    }
}

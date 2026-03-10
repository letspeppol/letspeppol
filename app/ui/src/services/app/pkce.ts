function randomString(length: number): string {
    const array = new Uint8Array(length);
    crypto.getRandomValues(array);
    return Array.from(array, b => 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~'[b % 66]).join('');
}

export function generateCodeVerifier(): string {
    return randomString(64);
}

export async function generateCodeChallenge(verifier: string): Promise<string> {
    const encoder = new TextEncoder();
    const data = encoder.encode(verifier);
    const digest = await crypto.subtle.digest('SHA-256', data);
    return btoa(String.fromCharCode(...new Uint8Array(digest)))
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=+$/, '');
}

export function generateState(): string {
    return randomString(32);
}

export function storePkce(verifier: string, state: string): void {
    sessionStorage.setItem('pkce_verifier', verifier);
    sessionStorage.setItem('pkce_state', state);
}

export function retrievePkce(): { verifier: string; state: string } | null {
    const verifier = sessionStorage.getItem('pkce_verifier');
    const state = sessionStorage.getItem('pkce_state');
    if (!verifier || !state) return null;
    sessionStorage.removeItem('pkce_verifier');
    sessionStorage.removeItem('pkce_state');
    return { verifier, state };
}

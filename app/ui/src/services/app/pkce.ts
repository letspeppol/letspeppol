import {base64urlEncode} from "../kyc/webauthn-utils";

function randomString(length: number): string {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
    const limit = 256 - (256 % chars.length); // 252 — reject bytes >= limit to avoid modulo bias
    const result: string[] = [];
    while (result.length < length) {
        const array = new Uint8Array(length - result.length);
        crypto.getRandomValues(array);
        for (const b of array) {
            if (b < limit && result.length < length) {
                result.push(chars[b % chars.length]);
            }
        }
    }
    return result.join('');
}

export function generateCodeVerifier(): string {
    return randomString(64);
}

export async function generateCodeChallenge(verifier: string): Promise<string> {
    const encoder = new TextEncoder();
    const data = encoder.encode(verifier);
    const digest = await crypto.subtle.digest('SHA-256', data);
    return base64urlEncode(digest);
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

import {singleton} from "aurelia";
import {
    deserializeRequestOptions,
    serializeAuthenticationCredential,
} from "./webauthn-utils";

const KYC_BASE = (import.meta.env.VITE_KYC_BASE_URL || '/kyc').replace(/\/$/, '');

export type BrowserAuthenticationStatus = 'anonymous' | 'totp_required' | 'authenticated';

export interface BrowserAuthenticationSession {
    status: BrowserAuthenticationStatus;
    csrfToken: string;
    csrfHeaderName: string;
    csrfParameterName: string;
}

interface StatusResponse {
    status: BrowserAuthenticationStatus;
}

interface ErrorResponse {
    errorCode?: string;
}

export class BrowserAuthenticationError extends Error {
    constructor(
        public readonly status: number,
        public readonly errorCode?: string,
    ) {
        super(errorCode || `Authentication request failed (${status})`);
        this.name = 'BrowserAuthenticationError';
    }
}

@singleton()
export class BrowserAuthenticationService {
    private session: BrowserAuthenticationSession | null = null;

    async getSession(): Promise<BrowserAuthenticationSession> {
        const response = await fetch(`${KYC_BASE}/auth/browser/session`, {
            method: 'GET',
            headers: {'Accept': 'application/json'},
            credentials: 'include',
            cache: 'no-store',
        });
        await this.throwIfFailed(response);
        const session = await response.json() as BrowserAuthenticationSession;
        if (!isAuthenticationStatus(session.status) || !session.csrfToken || !session.csrfHeaderName) {
            throw new BrowserAuthenticationError(response.status);
        }
        this.session = session;
        return session;
    }

    async authenticateWithPassword(username: string, password: string): Promise<BrowserAuthenticationStatus> {
        const session = await this.ensureSession();
        const body = new URLSearchParams({username, password});
        body.set(session.csrfParameterName || '_csrf', session.csrfToken);

        const response = await fetch(`${KYC_BASE}/auth/browser/login`, {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/x-www-form-urlencoded',
                [session.csrfHeaderName]: session.csrfToken,
            },
            credentials: 'include',
            body: body.toString(),
        });
        await this.throwIfFailed(response);
        const status = await this.readStatus(response);

        // Spring rotates the session and CSRF token after password authentication. Refresh it
        // before the second factor is submitted.
        if (status === 'totp_required') {
            await this.getSession();
        } else if (this.session) {
            this.session.status = status;
        }
        return status;
    }

    async verifyTotp(code: string): Promise<BrowserAuthenticationStatus> {
        const response = await this.postJson('/auth/browser/totp', {code});
        const status = await this.readStatus(response);
        if (this.session) this.session.status = status;
        return status;
    }

    async getPasskeyAuthenticationOptions(): Promise<PublicKeyCredentialRequestOptions> {
        const response = await this.postJson('/auth/browser/passkeys/authenticate/options', {});
        return deserializeRequestOptions(await response.json());
    }

    async verifyPasskey(credential: PublicKeyCredential): Promise<BrowserAuthenticationStatus> {
        const response = await this.postJson(
            '/auth/browser/passkeys/authenticate/verify',
            serializeAuthenticationCredential(credential),
        );
        const status = await this.readStatus(response);
        if (this.session) this.session.status = status;
        return status;
    }

    private async postJson(path: string, body: object): Promise<Response> {
        const session = await this.ensureSession();
        const response = await fetch(`${KYC_BASE}${path}`, {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json',
                [session.csrfHeaderName]: session.csrfToken,
            },
            credentials: 'include',
            body: JSON.stringify(body),
        });
        await this.throwIfFailed(response);
        return response;
    }

    private async ensureSession(): Promise<BrowserAuthenticationSession> {
        return this.session ?? this.getSession();
    }

    private async readStatus(response: Response): Promise<BrowserAuthenticationStatus> {
        const data = await response.json() as StatusResponse;
        if (!isAuthenticationStatus(data.status)) {
            throw new BrowserAuthenticationError(response.status);
        }
        return data.status;
    }

    private async throwIfFailed(response: Response): Promise<void> {
        if (response.ok) return;
        let errorCode: string | undefined;
        try {
            errorCode = (await response.clone().json() as ErrorResponse).errorCode;
        } catch {
            // Keep authentication errors deliberately generic when the server has no JSON body.
        }
        throw new BrowserAuthenticationError(response.status, errorCode);
    }
}

function isAuthenticationStatus(status: unknown): status is BrowserAuthenticationStatus {
    return status === 'anonymous' || status === 'totp_required' || status === 'authenticated';
}

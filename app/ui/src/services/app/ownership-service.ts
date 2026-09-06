import {resolve} from "@aurelia/kernel";
import {singleton} from "aurelia";
import {jwtDecode} from "jwt-decode";
import {KYCApi} from "../kyc/kyc-api";
import {CompanyService} from "./company-service";
import {
    clearRememberedOwnership,
    getRememberedOwnershipType,
    rememberActingOwnership,
} from "./ownership-route";

export interface OwnershipSummary {
    peppolId: string,
    companyName: string,
    type: string,
    peppolActive: boolean
}

interface JwtClaims {
    peppolId?: string,
    accountType?: string
}

@singleton()
export class OwnershipService {
    private static readonly STORAGE_KEY = 'accountOptions';
    private readonly kycApi = resolve(KYCApi);
    private readonly companyService = resolve(CompanyService);
    // Access tokens live in memory only (see LoginService), so the acting ownership is read from the
    // token LoginService hands us rather than from localStorage.
    private currentToken: string | null = null;
    private loadedToken: string | null = null;
    private loadingPromise: Promise<OwnershipSummary[]> | null = null;

    public ownerships: OwnershipSummary[] = [];

    constructor() {
        this.restoreOwnershipsFromStorage();
    }

    /** Called by LoginService whenever the access token changes (login, silent renewal, swap, logout). */
    onTokenChanged(token: string | null) {
        this.currentToken = token;
        if (!token) {
            clearRememberedOwnership();
            this.clearOwnerships();
            return;
        }
        const claims = this.claims();
        if (claims?.peppolId && claims.accountType) {
            rememberActingOwnership(claims.peppolId, claims.accountType);
        }
        void this.loadOwnerships(true);
    }

    async loadOwnerships(forceReload = false): Promise<OwnershipSummary[]> {
        const token = this.currentToken;
        if (!token) {
            this.clearOwnerships();
            return [];
        }

        if (!forceReload && this.loadedToken === token) {
            return this.ownerships;
        }

        if (!forceReload && this.loadingPromise) {
            return this.loadingPromise;
        }

        this.loadingPromise = this.kycApi.httpClient.get(`/sapi/account/ownerships`)
            .then(response => response.json())
            .then((ownerships: OwnershipSummary[]) => {
                this.ownerships = ownerships;
                this.loadedToken = token;
                this.persistOwnershipsToStorage();
                return ownerships;
            })
            .finally(() => {
                this.loadingPromise = null;
            });

        return this.loadingPromise;
    }

    getCachedOwnerships(): OwnershipSummary[] {
        return this.ownerships;
    }

    clearOwnerships() {
        this.ownerships = [];
        this.currentToken = null;
        this.loadedToken = null;
        this.loadingPromise = null;
        localStorage.removeItem(OwnershipService.STORAGE_KEY);
    }

    private persistOwnershipsToStorage() {
        localStorage.setItem(OwnershipService.STORAGE_KEY, JSON.stringify(this.ownerships));
    }

    private restoreOwnershipsFromStorage() {
        const serialized = localStorage.getItem(OwnershipService.STORAGE_KEY);
        if (!serialized) {
            return;
        }
        try {
            const parsed = JSON.parse(serialized);
            if (Array.isArray(parsed)) {
                this.ownerships = parsed;
            }
        } catch {
            localStorage.removeItem(OwnershipService.STORAGE_KEY);
        }
    }

    private claims(): JwtClaims | null {
        if (!this.currentToken) {
            return null;
        }
        try {
            return jwtDecode<JwtClaims>(this.currentToken);
        } catch {
            return null;
        }
    }

    getCurrentOwnershipKey(): string | null {
        const claims = this.claims();
        if (!claims?.peppolId || !claims.accountType) {
            return null;
        }
        return this.getOwnershipKey(claims.peppolId, claims.accountType);
    }

    getCurrentOwnershipType(): string | null {
        return this.claims()?.accountType ?? null;
    }

    getCurrentPeppolId(): string | null {
        return this.claims()?.peppolId ?? null;
    }

    getRememberedOwnershipType(peppolId: string): string | null {
        const claims = this.claims();
        if (claims?.peppolId === peppolId && claims.accountType) {
            return claims.accountType;
        }
        return getRememberedOwnershipType(peppolId);
    }

    rememberOwnership(selection: Pick<OwnershipSummary, 'peppolId' | 'type'>): void {
        rememberActingOwnership(selection.peppolId, selection.type);
    }

    getOwnershipKey(peppolId: string, type: string): string {
        return `${peppolId}::${type}`;
    }

    /**
     * Records the company/role as the server-side default. LoginService.swapOwnership() also sends
     * this exact selection on its silent authorization request; lastUsed is not the token input.
     */
    async selectOwnership(selection: OwnershipSummary): Promise<void> {
        await this.kycApi.httpClient.post(
            `/sapi/account/ownership`,
            JSON.stringify({peppolId: selection.peppolId, type: selection.type})
        );
    }

    /** Reloads the company bound to the current token (used after a swap). */
    async refreshCompanyContext(): Promise<void> {
        const company = await this.companyService.getAndSetMyCompanyForToken();
        localStorage.setItem('peppolActive', String(company.peppolActive));
    }
}

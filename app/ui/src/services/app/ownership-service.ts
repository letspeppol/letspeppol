import {resolve} from "@aurelia/kernel";
import {singleton} from "aurelia";
import {jwtDecode} from "jwt-decode";
import {KYCApi} from "../kyc/kyc-api";
import {CompanyService} from "./company-service";
import {AppApi} from "./app-api";

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
    private readonly appApi = resolve(AppApi);
    private readonly companyService = resolve(CompanyService);
    private loadedToken: string | null = null;
    private loadingPromise: Promise<OwnershipSummary[]> | null = null;

    public ownerships: OwnershipSummary[] = [];

    constructor() {
        this.restoreOwnershipsFromStorage();
    }

    async loadOwnerships(forceReload = false): Promise<OwnershipSummary[]> {
        const token = localStorage.getItem('token');
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

    getCurrentOwnershipKey(): string | null {
        const token = localStorage.getItem('token');
        if (!token) {
            return null;
        }
        const claims = jwtDecode<JwtClaims>(token);
        if (!claims.peppolId || !claims.accountType) {
            return null;
        }
        return this.getOwnershipKey(claims.peppolId, claims.accountType);
    }

    getCurrentOwnershipType(): string | null {
        const token = localStorage.getItem('token');
        if (!token) {
            return null;
        }
        const claims = jwtDecode<JwtClaims>(token);
        return claims.accountType ?? null;
    }

    getOwnershipKey(peppolId: string, type: string): string {
        return `${peppolId}::${type}`;
    }

    async swapOwnership(selection: OwnershipSummary): Promise<void> {
        const response = await this.kycApi.httpClient.post(
            `/sapi/jwt/swap`,
            JSON.stringify({ peppolId: selection.peppolId, type: selection.type })
        );
        const token = await response.text();
        localStorage.setItem('token', token);
        this.kycApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': `Bearer ${token}`} }));
        this.appApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': `Bearer ${token}`} }));
        const company = await this.companyService.getAndSetMyCompanyForToken();
        localStorage.setItem('peppolActive', String(company.peppolActive));
    }
}

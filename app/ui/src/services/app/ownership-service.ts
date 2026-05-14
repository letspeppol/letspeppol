import {resolve} from "@aurelia/kernel";
import {singleton} from "aurelia";
import {jwtDecode} from "jwt-decode";
import {KYCApi} from "../kyc/kyc-api";
import {LoginService} from "./login-service";
import {CompanyService} from "./company-service";

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
    private readonly kycApi = resolve(KYCApi);
    private readonly loginService = resolve(LoginService);
    private readonly companyService = resolve(CompanyService);

    public ownerships: OwnershipSummary[] = [];

    async loadOwnerships(): Promise<OwnershipSummary[]> {
        if (!this.loginService.authenticated) {
            this.ownerships = [];
            return this.ownerships;
        }
        this.ownerships = await this.kycApi.httpClient.get(`/sapi/account/ownerships`).then(response => response.json());
        return this.ownerships;
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

    getOwnershipKey(peppolId: string, type: string): string {
        return `${peppolId}::${type}`;
    }

    async swapOwnership(selection: OwnershipSummary): Promise<void> {
        const response = await this.kycApi.httpClient.post(
            `/sapi/jwt/swap`,
            JSON.stringify({ peppolId: selection.peppolId, type: selection.type })
        );
        const token = await response.text();
        this.loginService.updateToken(token);
        const company = await this.companyService.getAndSetMyCompanyForToken();
        localStorage.setItem('peppolActive', String(company.peppolActive));
        await this.loadOwnerships();
    }
}

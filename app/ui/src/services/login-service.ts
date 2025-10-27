import {resolve} from "@aurelia/kernel";
import {singleton} from "aurelia";
import {ProxyApi} from "./api/proxy-api";
import {KYCApi} from "./api/kyc-api";
import jwt, { JwtPayload } from "jsonwebtoken";
import {AppApi} from "./api/app-api";

@singleton()
export class LoginService {
    public kycApi = resolve(KYCApi);
    public proxyApi = resolve(ProxyApi);
    public appApi = resolve(AppApi);
    public authenticated = false;

    constructor() {
        this.verifyAuthenticated();
    }

    verifyAuthenticated() {
        const token = localStorage.getItem('token');
        if (token && !this.isExpired(token)) {
            this.authenticated = true;
        }
    }

    isExpired(token: string): boolean {
        const decoded = jwt.decode(token) as JwtPayload | null;
        if (!decoded || !decoded.exp) return true;

        const now = Math.floor(Date.now() / 1000);
        return decoded.exp < now;
    }

    async auth(username: string, password: string) : Promise<void> {
        let token = await this.getJwtToken(username, password);
        localStorage.setItem('token', token);
        this.setAuthHeader(token);
        this.authenticated = true;
    }

    async getJwtToken(username: string, password: string) {
        let authHeaders: Headers = new Headers;
        authHeaders.append('Authorization', `Basic ${Buffer.from(username + ":" + password).toString('base64')}` );
        const requestInit: RequestInit = { headers: authHeaders }
        const response = await this.kycApi.httpClient.post(`/api/jwt/auth`, undefined, requestInit);
        return await response.text();
    }

    setAuthHeader(token: string) {
        this.kycApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': `Bearer ${token}`} }));
        this.proxyApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': `Bearer ${token}`} }));
        this.appApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': `Bearer ${token}`} }));
    }

    logout() {
        this.kycApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': ''} }));
        this.proxyApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': ''} }));
        this.appApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': ''} }));
        localStorage.removeItem('token');
    }
}

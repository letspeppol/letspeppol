import {resolve} from "@aurelia/kernel";
import {singleton} from "aurelia";
import {jwtDecode} from "jwt-decode";
import {KYCApi} from "../kyc/kyc-api";
import {ProxyApi} from "../proxy/proxy-api";
import {AppApi} from "./app-api";

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

    getTokenExpiryDateInSeconds(token: string): number {
        const decoded = jwtDecode(token);
        if (!decoded || !decoded.exp) {
            return 0;
        }
        return decoded.exp;
    }

    isExpired(token: string): boolean {
        return this.getTokenExpiryDateInSeconds(token) < this.getCurrentDateInSeconds();
    }

    getCurrentDateInSeconds() {
        return Math.floor(Date.now() / 1000);
    }

    async auth(username: string, password: string) : Promise<void> {
        const token = await this.getJwtToken(username, password);
        localStorage.setItem('token', token);
        this.setAuthHeader(token);
        this.authenticated = true;
    }

    async getJwtToken(username: string, password: string) {
        const authHeaders: Headers = new Headers;
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
        this.authenticated = false;
    }
}

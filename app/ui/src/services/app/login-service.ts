import {resolve} from "@aurelia/kernel";
import {singleton} from "aurelia";
import {jwtDecode} from "jwt-decode";
import {KYCApi} from "../kyc/kyc-api";
import {AppApi} from "./app-api";
import {generateCodeVerifier, generateCodeChallenge, generateState, storePkce, retrievePkce} from "./pkce";

const KYC_BASE = '/kyc';
const CLIENT_ID = 'letspeppol-ui';
const CALLBACK_PATH = '/callback';

@singleton()
export class LoginService {
    public kycApi = resolve(KYCApi);
    public appApi = resolve(AppApi);
    public authenticated = false;

    constructor() {
        this.verifyAuthenticated();
    }

    verifyAuthenticated() {
        const token = localStorage.getItem('token');
        if (token && !this.isExpired(token)) {
            this.setAuthHeader(token);
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

    async initiateLogin(): Promise<void> {
        const verifier = generateCodeVerifier();
        const challenge = await generateCodeChallenge(verifier);
        const state = generateState();
        storePkce(verifier, state);

        const redirectUri = `${window.location.origin}${CALLBACK_PATH}`;
        const params = new URLSearchParams({
            response_type: 'code',
            client_id: CLIENT_ID,
            redirect_uri: redirectUri,
            code_challenge: challenge,
            code_challenge_method: 'S256',
            state: state,
            scope: 'openid',
        });

        window.location.href = `${KYC_BASE}/oauth2/authorize?${params.toString()}`;
    }

    async handleCallback(code: string, state: string): Promise<void> {
        const pkce = retrievePkce();
        if (!pkce || pkce.state !== state) {
            throw new Error('Invalid state parameter');
        }

        const redirectUri = `${window.location.origin}${CALLBACK_PATH}`;
        const body = new URLSearchParams({
            grant_type: 'authorization_code',
            client_id: CLIENT_ID,
            code: code,
            redirect_uri: redirectUri,
            code_verifier: pkce.verifier,
        });

        const response = await fetch(`${KYC_BASE}/oauth2/token`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: body.toString(),
        });

        if (!response.ok) {
            throw new Error('Token exchange failed');
        }

        const data = await response.json();
        localStorage.setItem('token', data.access_token);
        if (data.refresh_token) {
            localStorage.setItem('refresh_token', data.refresh_token);
        }
        if (data.id_token) {
            localStorage.setItem('id_token', data.id_token);
        }
        this.setAuthHeader(data.access_token);
        this.authenticated = true;
    }

    async refreshToken(): Promise<boolean> {
        const refreshToken = localStorage.getItem('refresh_token');
        if (!refreshToken) return false;

        const body = new URLSearchParams({
            grant_type: 'refresh_token',
            client_id: CLIENT_ID,
            refresh_token: refreshToken,
        });

        try {
            const response = await fetch(`${KYC_BASE}/oauth2/token`, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: body.toString(),
            });

            if (!response.ok) return false;

            const data = await response.json();
            localStorage.setItem('token', data.access_token);
            if (data.refresh_token) {
                localStorage.setItem('refresh_token', data.refresh_token);
            }
            this.setAuthHeader(data.access_token);
            this.authenticated = true;
            return true;
        } catch {
            return false;
        }
    }

    updateToken(token: string) {
        localStorage.setItem('token', token);
        this.setAuthHeader(token);
        this.verifyAuthenticated();
    }

    setAuthHeader(token: string) {
        this.kycApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': `Bearer ${token}`} }));
        this.appApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': `Bearer ${token}`} }));
    }

    logout(redirectToAuthServer = true) {
        this.kycApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': ''} }));
        this.appApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': ''} }));
        const idToken = localStorage.getItem('id_token');
        localStorage.removeItem('token');
        localStorage.removeItem('refresh_token');
        localStorage.removeItem('id_token');
        localStorage.removeItem('peppolActive');
        this.authenticated = false;

        if (redirectToAuthServer && idToken) {
            const postLogoutRedirect = encodeURIComponent(window.location.origin + '/login');
            window.location.href = `${KYC_BASE}/connect/logout?id_token_hint=${encodeURIComponent(idToken)}&post_logout_redirect_uri=${postLogoutRedirect}&client_id=${CLIENT_ID}`;
        }
    }
}

import {resolve} from "@aurelia/kernel";
import {singleton} from "aurelia";
import {jwtDecode} from "jwt-decode";
import {KYCApi} from "../kyc/kyc-api";
import {AppApi} from "./app-api";
import {generateCodeVerifier, generateCodeChallenge, generateState, storePkce, retrievePkce} from "./pkce";
import {PartnerService} from "./partner-service";
import {SponsorService} from "./sponsor-service";
import {StatisticsService} from "./statistics-service";

const KYC_BASE = '/kyc';
const CLIENT_ID = 'letspeppol-ui';
const CALLBACK_PATH = '/callback';
const LOGIN_PATH = '/login';
const PEPPOL_ACTIVE_KEY = 'peppolActive';
// Non-sensitive marker (not a token) that this browser has had a session, so eager silent re-auth
// only runs when there's plausibly a KYC session to ride.
const SESSION_HINT_KEY = 'session_hint';

interface TokenResponse {
    access_token: string;
    id_token?: string;
}

@singleton()
export class LoginService {
    public kycApi = resolve(KYCApi);
    public appApi = resolve(AppApi);
    private partnerService = resolve(PartnerService);
    private sponsorService = resolve(SponsorService);
    private statisticsService = resolve(StatisticsService);
    public authenticated = false;

    // Tokens live in memory only (never localStorage) so an XSS foothold can't read them at rest;
    // session continuity rides the HttpOnly KYC cookie via silent re-authorization (see silentLogin()).
    private accessToken: string | null = null;
    private idToken: string | null = null;
    private silentLoginInFlight: Promise<boolean> | null = null;

    constructor() {
        // Fresh page load: in-memory tokens are gone, so restore silently — but skip the callback (it
        // runs its own exchange), the login page, and anonymous visitors (no session hint). Protected
        // routes still restore via ensureAuthenticated() in the router hook.
        const path = window.location.pathname;
        if (path !== CALLBACK_PATH && path !== LOGIN_PATH && localStorage.getItem(SESSION_HINT_KEY)) {
            void this.silentLogin();
        }
    }

    private get redirectUri(): string {
        return `${window.location.origin}${CALLBACK_PATH}`;
    }

    get token(): string | null {
        return this.accessToken;
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

    /** True if a usable access token is available (restoring it via silent login if needed). */
    async ensureAuthenticated(): Promise<boolean> {
        if (this.accessToken && !this.isExpired(this.accessToken)) return true;
        return this.silentLogin();
    }

    async initiateLogin(): Promise<void> {
        const verifier = generateCodeVerifier();
        const challenge = await generateCodeChallenge(verifier);
        const state = generateState();
        storePkce(verifier, state);

        const params = new URLSearchParams({
            response_type: 'code',
            client_id: CLIENT_ID,
            redirect_uri: this.redirectUri,
            code_challenge: challenge,
            code_challenge_method: 'S256',
            state: state,
            scope: 'openid',
        });

        this.clearCachedData();
        window.location.href = `${KYC_BASE}/oauth2/authorize?${params.toString()}`;
    }

    async handleCallback(code: string, state: string): Promise<void> {
        const pkce = retrievePkce();
        if (!pkce || pkce.state !== state) {
            throw new Error('Invalid state parameter');
        }
        await this.exchangeCode(code, pkce.verifier);
    }

    /**
     * Silent re-authorization: ride the HttpOnly KYC session to mint a fresh code with no UI
     * (prompt=none). We follow the same-origin redirect and read the code off the final URL; with no
     * valid session there's no code, so we report failure and the caller falls back to interactive login.
     */
    async silentLogin(): Promise<boolean> {
        if (this.silentLoginInFlight) return this.silentLoginInFlight;
        this.silentLoginInFlight = this.doSilentLogin().finally(() => {
            this.silentLoginInFlight = null;
        });
        return this.silentLoginInFlight;
    }

    private async doSilentLogin(): Promise<boolean> {
        try {
            const verifier = generateCodeVerifier();
            const challenge = await generateCodeChallenge(verifier);
            const state = generateState();

            const params = new URLSearchParams({
                response_type: 'code',
                client_id: CLIENT_ID,
                redirect_uri: this.redirectUri,
                code_challenge: challenge,
                code_challenge_method: 'S256',
                state: state,
                scope: 'openid',
                prompt: 'none',
            });

            const response = await fetch(`${KYC_BASE}/oauth2/authorize?${params.toString()}`, {
                method: 'GET',
                credentials: 'same-origin',
                redirect: 'follow',
            });

            const finalUrl = new URL(response.url, window.location.origin);
            // Only trust a code from a same-origin final URL (redirect_uri is same-origin by construction).
            if (finalUrl.origin !== window.location.origin) {
                return false;
            }
            const code = finalUrl.searchParams.get('code');
            const returnedState = finalUrl.searchParams.get('state');
            if (!code || returnedState !== state) {
                return false;
            }
            await this.exchangeCode(code, verifier);
            return true;
        } catch {
            return false;
        }
    }

    async refreshToken(): Promise<boolean> {
        // Renewal now rides the KYC session instead of a stored refresh token.
        return this.silentLogin();
    }

    private async exchangeCode(code: string, verifier: string): Promise<void> {
        const body = new URLSearchParams({
            grant_type: 'authorization_code',
            client_id: CLIENT_ID,
            code: code,
            redirect_uri: this.redirectUri,
            code_verifier: verifier,
        });

        const response = await fetch(`${KYC_BASE}/oauth2/token`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: body.toString(),
        });

        if (!response.ok) {
            throw new Error('Token exchange failed');
        }

        const data: TokenResponse = await response.json();
        this.storeTokenResponse(data);
    }

    updateToken(token: string) {
        this.clearCachedData();
        this.applyAccessToken(token);
    }

    private storeTokenResponse(data: TokenResponse) {
        // id_token is kept (in memory) as the logout id_token_hint; any refresh token is ignored —
        // silent re-authorization replaces it, so no long-lived secret is stored in the browser.
        if (data.id_token) {
            this.idToken = data.id_token;
        }
        this.applyAccessToken(data.access_token);
    }

    private applyAccessToken(token: string) {
        this.accessToken = token;
        this.setAuthHeader(token);
        this.authenticated = true;
        localStorage.setItem(SESSION_HINT_KEY, '1');
    }

    setAuthHeader(token: string) {
        this.kycApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': `Bearer ${token}`} }));
        this.appApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': `Bearer ${token}`} }));
    }

    logout(redirectToAuthServer = true) {
        this.clearCachedData();
        this.kycApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': ''} }));
        this.appApi.httpClient.configure(config => config.withDefaults({ headers: {'Authorization': ''} }));
        const idToken = this.idToken;
        this.accessToken = null;
        this.idToken = null;
        localStorage.removeItem(PEPPOL_ACTIVE_KEY);
        localStorage.removeItem(SESSION_HINT_KEY);
        this.authenticated = false;

        if (redirectToAuthServer && idToken) {
            const postLogoutRedirect = encodeURIComponent(window.location.origin + '/login');
            window.location.href = `${KYC_BASE}/connect/logout?id_token_hint=${encodeURIComponent(idToken)}&post_logout_redirect_uri=${postLogoutRedirect}&client_id=${CLIENT_ID}`;
        }
    }

    private clearCachedData() {
        this.partnerService.clearCache();
        this.sponsorService.clearCache();
        this.statisticsService.clearCache();
    }
}

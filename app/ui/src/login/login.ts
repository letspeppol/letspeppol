import {resolve} from "@aurelia/kernel";
import {LoginService} from "../services/app/login-service";
import {
    BrowserAuthenticationError,
    BrowserAuthenticationService,
} from "../services/kyc/browser-authentication-service";

const REMEMBERED_EMAIL_KEY = 'peppol.login.email.v1';

type LoginStep = 'credentials' | 'totp';

export class Login {
    private readonly loginService = resolve(LoginService);
    private readonly browserAuthentication = resolve(BrowserAuthenticationService);

    step: LoginStep = 'credentials';
    email = localStorage.getItem(REMEMBERED_EMAIL_KEY) ?? '';
    password = '';
    rememberEmail = this.email.length > 0;
    verificationCode = '';
    useRecoveryCode = false;
    loadingSession = true;
    serviceUnavailable = false;
    busy = false;
    errorKey: string | undefined;
    readonly passkeyAvailable = typeof PublicKeyCredential !== 'undefined'
        && typeof navigator.credentials?.get === 'function';

    async attached() {
        if (this.loginService.authenticated) {
            this.loginService.logout();
            return;
        }

        await this.loadSession();
    }

    async retrySession() {
        if (this.loadingSession) return;
        await this.loadSession();
    }

    private async loadSession() {
        this.loadingSession = true;
        this.serviceUnavailable = false;
        this.errorKey = undefined;
        try {
            const session = await this.browserAuthentication.getSession();
            if (session.status === 'authenticated') {
                await this.continueWithOAuth();
                return;
            }
            this.step = session.status === 'totp_required' ? 'totp' : 'credentials';
        } catch {
            // This is especially common during a local compound launch, where Vite is ready before
            // the KYC service. Keep the login form hidden until its CSRF/session endpoint is ready.
            this.serviceUnavailable = true;
        } finally {
            this.loadingSession = false;
        }
    }

    async submitCredentials() {
        const email = this.email.trim();
        if (this.busy || !email || !this.password) return;

        this.busy = true;
        this.errorKey = undefined;
        if (!this.rememberEmail) localStorage.removeItem(REMEMBERED_EMAIL_KEY);

        try {
            const status = await this.browserAuthentication.authenticateWithPassword(email, this.password);
            if (status !== 'authenticated' && status !== 'totp_required') {
                throw new BrowserAuthenticationError(401);
            }

            if (this.rememberEmail) localStorage.setItem(REMEMBERED_EMAIL_KEY, email);
            this.password = '';

            if (status === 'totp_required') {
                this.step = 'totp';
                this.verificationCode = '';
                return;
            }
            await this.continueWithOAuth();
        } catch (error) {
            this.setAuthenticationError(error, 'login.error');
        } finally {
            this.busy = false;
        }
    }

    async signInWithPasskey() {
        if (this.busy || !this.passkeyAvailable) return;

        this.busy = true;
        this.errorKey = undefined;
        try {
            const publicKey = await this.browserAuthentication.getPasskeyAuthenticationOptions();
            const credential = await navigator.credentials.get({publicKey});
            if (!credential) return;

            const status = await this.browserAuthentication.verifyPasskey(credential as PublicKeyCredential);
            if (status !== 'authenticated') {
                throw new BrowserAuthenticationError(401);
            }
            await this.continueWithOAuth();
        } catch (error) {
            if (!isPasskeyCancellation(error)) {
                this.setAuthenticationError(error, 'login.passkey-error');
            }
        } finally {
            this.busy = false;
        }
    }

    async submitVerificationCode() {
        const enteredCode = this.verificationCode.trim();
        const code = this.useRecoveryCode ? enteredCode.toUpperCase() : enteredCode;
        if (this.busy || !code) return;

        this.busy = true;
        this.errorKey = undefined;
        try {
            const status = await this.browserAuthentication.verifyTotp(code);
            if (status !== 'authenticated') {
                throw new BrowserAuthenticationError(401);
            }
            await this.continueWithOAuth();
        } catch (error) {
            this.setAuthenticationError(error, 'login.verification-error');
        } finally {
            this.busy = false;
        }
    }

    toggleRecoveryCode(event: Event) {
        event.preventDefault();
        this.useRecoveryCode = !this.useRecoveryCode;
        this.verificationCode = '';
        this.errorKey = undefined;
    }

    private async continueWithOAuth() {
        await this.loginService.initiateLogin();
    }

    private setAuthenticationError(error: unknown, fallbackKey: string) {
        this.errorKey = error instanceof BrowserAuthenticationError && error.status === 429
            ? 'error.too_many_requests'
            : fallbackKey;
    }
}

export function isPasskeyCancellation(error: unknown): boolean {
    const name = typeof error === 'object' && error !== null && 'name' in error
        ? String((error as {name: unknown}).name)
        : '';
    return name === 'AbortError' || name === 'NotAllowedError';
}

import {resolve} from "@aurelia/kernel";
import {IRouter} from "@aurelia/router";
import {LoginService} from "../services/app/login-service";
import {
    BrowserAuthenticationError,
    BrowserAuthenticationService,
} from "../services/kyc/browser-authentication-service";
import {consumeLoginError, loginErrorKeyFor} from "./pending-login-error";

const VERIFICATION_CODE_LENGTH = 6;

type LoginStep = 'credentials' | 'totp';

export class Login {
    private readonly loginService = resolve(LoginService);
    private readonly browserAuthentication = resolve(BrowserAuthenticationService);
    private readonly router = resolve(IRouter);

    step: LoginStep = 'credentials';
    email = '';
    password = '';
    verificationCode = '';
    useRecoveryCode = false;
    loadingSession = true;
    serviceUnavailable = false;
    busy = false;
    errorKey: string | undefined;
    private passkeyAutofill = false;
    private authorizationErrorKey: string | undefined;
    private passkeyAutofillRequest: AbortController | undefined;
    readonly passkeyAvailable = typeof PublicKeyCredential !== 'undefined'
        && typeof navigator.credentials?.get === 'function';

    async attached() {
        if (this.loginService.authenticated) {
            this.loginService.logout();
            return;
        }

        this.authorizationErrorKey = consumeLoginError();
        this.passkeyAutofill = await passkeyAutofillSupported();
        if (await this.loadSession()) {
            await this.startPasskeyAutofill();
        }
    }

    detaching() {
        this.cancelPasskeyAutofill();
    }

    async retrySession() {
        if (this.loadingSession) return;
        if (await this.loadSession()) {
            await this.startPasskeyAutofill();
        }
    }

    private async loadSession(): Promise<boolean> {
        this.loadingSession = true;
        this.serviceUnavailable = false;
        this.errorKey = this.authorizationErrorKey;
        try {
            const session = await this.browserAuthentication.getSession();
            if (session.status === 'authenticated' && !this.authorizationErrorKey) {
                return !await this.continueWithOAuth();
            }
            this.step = session.status === 'totp_required' ? 'totp' : 'credentials';
            return true;
        } catch {
            // This is especially common during a local compound launch, where Vite is ready before
            // the KYC service. Keep the login form hidden until its CSRF/session endpoint is ready.
            this.serviceUnavailable = true;
            return false;
        } finally {
            this.loadingSession = false;
        }
    }

    async submitCredentials() {
        const email = this.email.trim();
        if (this.busy || !email || !this.password) return;

        this.busy = true;
        this.cancelPasskeyAutofill();
        this.clearErrors();

        try {
            const status = await this.browserAuthentication.authenticateWithPassword(email, this.password);
            if (status !== 'authenticated' && status !== 'totp_required') {
                throw new BrowserAuthenticationError(401);
            }

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
        this.cancelPasskeyAutofill();
        this.clearErrors();
        try {
            const publicKey = await this.browserAuthentication.getPasskeyAuthenticationOptions();
            const credential = await navigator.credentials.get({publicKey});
            if (!credential) return;

            await this.completePasskeySignIn(credential as PublicKeyCredential);
        } catch (error) {
            if (!isPasskeyCancellation(error)) {
                this.setAuthenticationError(error, 'login.passkey-error');
            }
        } finally {
            this.busy = false;
        }
    }

    private async startPasskeyAutofill() {
        if (!this.passkeyAutofill || this.step !== 'credentials') return;

        await afterRender();
        void this.offerPasskeyAutofill();
    }

    private async offerPasskeyAutofill() {
        this.passkeyAutofillRequest = new AbortController();
        const signal = this.passkeyAutofillRequest.signal;
        try {
            const publicKey = await this.browserAuthentication.getPasskeyAuthenticationOptions();
            if (signal.aborted) return;

            const credential = await navigator.credentials.get({publicKey, mediation: 'conditional', signal});
            if (!credential || signal.aborted) return;

            this.busy = true;
            await this.completePasskeySignIn(credential as PublicKeyCredential);
        } catch (error) {
            if (!isPasskeyCancellation(error)) {
                this.setAuthenticationError(error, 'login.passkey-error');
            }
        } finally {
            this.busy = false;
        }
    }

    private async completePasskeySignIn(credential: PublicKeyCredential) {
        const status = await this.browserAuthentication.verifyPasskey(credential);
        if (status !== 'authenticated') {
            throw new BrowserAuthenticationError(401);
        }
        await this.continueWithOAuth();
    }

    private cancelPasskeyAutofill() {
        this.passkeyAutofillRequest?.abort();
        this.passkeyAutofillRequest = undefined;
    }

    onVerificationCodeInput(event: Event) {
        const input = event.target as HTMLInputElement;
        const digits = input.value.replace(/\D/g, '').slice(0, VERIFICATION_CODE_LENGTH);
        input.value = digits;
        this.verificationCode = digits;

        if (digits.length === VERIFICATION_CODE_LENGTH) {
            void this.submitVerificationCode();
        }
    }

    onRecoveryCodeInput(event: Event) {
        const input = event.target as HTMLInputElement;
        const normalized = input.value.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
        input.value = normalized;
        this.verificationCode = normalized;
    }

    async submitVerificationCode() {
        const code = this.verificationCode.trim();
        if (this.busy || !code) return;

        this.busy = true;
        this.clearErrors();
        try {
            const status = await this.browserAuthentication.verifyTotp(code);
            if (status !== 'authenticated') {
                throw new BrowserAuthenticationError(401);
            }
            await this.continueWithOAuth();
        } catch (error) {
            this.verificationCode = '';
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

    private clearErrors() {
        this.errorKey = undefined;
        this.authorizationErrorKey = undefined;
    }

    private async continueWithOAuth(): Promise<boolean> {
        const {authorized, reason} = await this.loginService.completeLogin();
        if (!authorized) {
            this.step = 'credentials';
            this.errorKey = loginErrorKeyFor(reason);
            return false;
        }
        void this.router.load(this.loginService.getPostLoginPath());
        return true;
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

async function passkeyAutofillSupported(): Promise<boolean> {
    return typeof PublicKeyCredential !== 'undefined'
        && typeof PublicKeyCredential.isConditionalMediationAvailable === 'function'
        && await PublicKeyCredential.isConditionalMediationAvailable();
}

function afterRender(): Promise<void> {
    return new Promise(resolve => requestAnimationFrame(() => resolve()));
}

import {resolve} from "@aurelia/kernel";
import {IRouter} from "@aurelia/router";
import {LoginService} from "../services/app/login-service";

export class Callback {
    private readonly loginService = resolve(LoginService);
    private readonly router = resolve(IRouter);
    message = 'Completing login...';

    async attached() {
        const params = new URLSearchParams(window.location.search);
        const code = params.get('code');
        const state = params.get('state');

        if (!code || !state) {
            this.message = 'Missing authorization parameters, redirecting...';
            window.location.href = '/login';
            return;
        }

        try {
            await this.loginService.handleCallback(code, state);
            // Keep the access token that was just stored in memory. Do not await this navigation:
            // the router cannot start it until the current /callback navigation (including this
            // attached hook) has completed, so awaiting it here would deadlock both navigations.
            void this.router.load('/');
        } catch (e) {
            console.error('Callback failed:', e);
            this.message = `Authentication failed: ${e instanceof Error ? e.message : e}`;
        }
    }
}

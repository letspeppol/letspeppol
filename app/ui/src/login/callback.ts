import {resolve} from "@aurelia/kernel";
import {LoginService} from "../services/app/login-service";

export class Callback {
    private readonly loginService = resolve(LoginService);
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
            window.location.href = '/';
        } catch (e) {
            console.error('Callback failed:', e);
            this.message = `Authentication failed: ${e instanceof Error ? e.message : e}`;
        }
    }
}

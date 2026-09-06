import {beforeEach, describe, expect, it} from 'vitest';
import {
    captureLoginError,
    consumeLoginError,
    GENERIC_LOGIN_ERROR_KEY,
    loginErrorKeyFor,
    rememberLoginError,
} from '../../src/login/pending-login-error';

const goTo = (url: string) => window.history.replaceState({}, '', url);

beforeEach(() => {
    sessionStorage.clear();
    goTo('/login');
});

describe('pending login error', () => {
    it('maps known reason codes and falls back to the generic key', () => {
        expect(loginErrorKeyFor('ownership_unavailable')).toBe('login.authorization-no-ownership');
        expect(loginErrorKeyFor('access_denied')).toBe('login.authorization-denied');
        expect(loginErrorKeyFor('temporarily_unavailable')).toBe('login.authorization-unavailable');
        expect(loginErrorKeyFor('something_new')).toBe(GENERIC_LOGIN_ERROR_KEY);
        expect(loginErrorKeyFor(null)).toBe(GENERIC_LOGIN_ERROR_KEY);
    });

    it('hands over a remembered failure exactly once', () => {
        rememberLoginError('login.authorization-error');
        expect(consumeLoginError()).toBe('login.authorization-error');
        expect(consumeLoginError()).toBeUndefined();
    });

    it('captures a KYC redirect reason and strips it from the address bar', () => {
        goTo('/login?error=ownership_unavailable');
        captureLoginError();

        expect(window.location.search).toBe('');
        expect(consumeLoginError()).toBe('login.authorization-no-ownership');
    });

    it('captures an OAuth error redirected to the callback', () => {
        goTo('/callback?error=access_denied&state=abc');
        captureLoginError();

        expect(window.location.search).toBe('?state=abc');
        expect(consumeLoginError()).toBe('login.authorization-denied');
    });

    it('never stores a key that was not mapped, whatever the server sent', () => {
        goTo('/login?error=%3Cimg+src%3Dx+onerror%3Dalert(1)%3E');
        captureLoginError();

        expect(consumeLoginError()).toBe(GENERIC_LOGIN_ERROR_KEY);
    });

    it('leaves other screens and their query strings alone', () => {
        goTo('/registration?error=access_denied');
        captureLoginError();

        expect(window.location.search).toBe('?error=access_denied');
        expect(consumeLoginError()).toBeUndefined();
    });

    it('ignores a login page reached without an error', () => {
        rememberLoginError('login.authorization-error');
        goTo('/login?lng=nl');
        captureLoginError();

        expect(window.location.search).toBe('?lng=nl');
        expect(consumeLoginError()).toBe('login.authorization-error');
    });
});

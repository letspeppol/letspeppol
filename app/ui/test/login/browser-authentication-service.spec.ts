import {afterEach, describe, expect, it, vi} from 'vitest';
import {
    BrowserAuthenticationError,
    BrowserAuthenticationService,
} from '../../src/services/kyc/browser-authentication-service';

const sessionResponse = (csrfToken: string, status = 'anonymous') => new Response(JSON.stringify({
    status,
    csrfToken,
    csrfHeaderName: 'X-CSRF-TOKEN',
    csrfParameterName: '_csrf',
}), {
    status: 200,
    headers: {'Content-Type': 'application/json'},
});

const statusResponse = (status: string, responseStatus = 200) => new Response(JSON.stringify({status}), {
    status: responseStatus,
    headers: {'Content-Type': 'application/json'},
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('BrowserAuthenticationService', () => {
    it('posts form credentials with CSRF and refreshes the rotated token before TOTP', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(sessionResponse('csrf-before'))
            .mockResolvedValueOnce(statusResponse('totp_required', 202))
            .mockResolvedValueOnce(sessionResponse('csrf-after', 'totp_required'))
            .mockResolvedValueOnce(statusResponse('authenticated'));
        vi.stubGlobal('fetch', fetchMock);

        const service = new BrowserAuthenticationService();
        expect(await service.authenticateWithPassword('user@example.com', 'secret')).toBe('totp_required');

        const [loginUrl, loginInit] = fetchMock.mock.calls[1];
        expect(loginUrl).toBe('/kyc/login');
        expect(loginInit.credentials).toBe('include');
        expect(loginInit.headers['Accept']).toBe('application/json');
        expect(loginInit.headers['Content-Type']).toBe('application/x-www-form-urlencoded');
        expect(loginInit.headers['X-CSRF-TOKEN']).toBe('csrf-before');
        expect(new URLSearchParams(loginInit.body)).toEqual(new URLSearchParams({
            username: 'user@example.com',
            password: 'secret',
            _csrf: 'csrf-before',
        }));

        expect(fetchMock.mock.calls[2][0]).toBe('/kyc/auth/session');
        expect(await service.verifyTotp('123456')).toBe('authenticated');

        const [totpUrl, totpInit] = fetchMock.mock.calls[3];
        expect(totpUrl).toBe('/kyc/auth/totp');
        expect(totpInit.credentials).toBe('include');
        expect(totpInit.headers['X-CSRF-TOKEN']).toBe('csrf-after');
        expect(JSON.parse(totpInit.body)).toEqual({code: '123456'});
    });

    it('surfaces structured authentication failures without using the KYC API interceptor', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(sessionResponse('csrf-token'))
            .mockResolvedValueOnce(new Response(JSON.stringify({errorCode: 'auth_failed'}), {
                status: 401,
                headers: {'Content-Type': 'application/json'},
            }));
        vi.stubGlobal('fetch', fetchMock);

        const service = new BrowserAuthenticationService();
        await expect(service.authenticateWithPassword('user@example.com', 'wrong'))
            .rejects.toMatchObject<Partial<BrowserAuthenticationError>>({
                status: 401,
                errorCode: 'auth_failed',
            });
    });

    it('sends passkey requests as CSRF-protected same-origin JSON calls', async () => {
        const fetchMock = vi.fn()
            .mockResolvedValueOnce(sessionResponse('csrf-token'))
            .mockResolvedValueOnce(new Response(JSON.stringify({
                challenge: 'AQID',
                rpId: 'example.com',
                timeout: 60_000,
                userVerification: 'required',
                allowCredentials: [],
            }), {
                status: 200,
                headers: {'Content-Type': 'application/json'},
            }));
        vi.stubGlobal('fetch', fetchMock);

        const options = await new BrowserAuthenticationService().getPasskeyAuthenticationOptions();

        expect(Array.from(new Uint8Array(options.challenge))).toEqual([1, 2, 3]);
        const [url, init] = fetchMock.mock.calls[1];
        expect(url).toBe('/kyc/auth/passkeys/authenticate/options');
        expect(init.credentials).toBe('include');
        expect(init.headers['X-CSRF-TOKEN']).toBe('csrf-token');
        expect(init.body).toBe('{}');
    });
});

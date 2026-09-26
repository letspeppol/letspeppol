import {describe, expect, it} from 'vitest';
import {refusalReason} from '../../src/services/app/login-service';

const jsonResponse = (body: unknown, status = 400) => new Response(JSON.stringify(body), {
    status,
    headers: {'Content-Type': 'application/json'},
});

const finalUrl = (url: string) => new URL(url, 'https://localpeppol.org');

describe('refusalReason', () => {
    it('reads the reason KYC redirected back to the callback', async () => {
        const reason = await refusalReason(
            jsonResponse({}, 200),
            finalUrl('/callback?error=login_required&state=abc'),
        );

        expect(reason).toBe('login_required');
    });

    it('reads the reason from the error body when there was no redirect', async () => {
        const reason = await refusalReason(
            jsonResponse({error: 'ownership_unavailable', error_description: 'gone'}),
            finalUrl('/kyc/auth/oauth2/authorize'),
        );

        expect(reason).toBe('ownership_unavailable');
    });

    it('prefers a redirected reason over the body', async () => {
        const reason = await refusalReason(
            jsonResponse({error: 'server_error'}),
            finalUrl('/callback?error=access_denied'),
        );

        expect(reason).toBe('access_denied');
    });

    it('has no reason for a successful response that simply carried no code', async () => {
        expect(await refusalReason(jsonResponse({}, 200), finalUrl('/login'))).toBeUndefined();
    });

    it('survives an error page that is not JSON', async () => {
        const response = new Response('<html>gateway timeout</html>', {status: 504});

        expect(await refusalReason(response, finalUrl('/kyc/auth/oauth2/authorize'))).toBeUndefined();
    });
});

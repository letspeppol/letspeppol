import {describe, expect, it} from 'vitest';
import {isMissingPermission} from '../../src/services/util/forbidden-response';

function jsonResponse(status: number, body: unknown): Response {
    return new Response(JSON.stringify(body), {status, headers: {'Content-Type': 'application/json'}});
}

describe('isMissingPermission', () => {
    it('recognises a missing permission from the app backend and keeps the body readable', async () => {
        const response = jsonResponse(403, {errorCode: 'MISSING_PERMISSION', message: 'Missing permission'});

        expect(await isMissingPermission(response)).toBe(true);
        expect(await response.json()).toEqual({errorCode: 'MISSING_PERMISSION', message: 'Missing permission'});
    });

    it('recognises the admin-only refusal from KYC', async () => {
        expect(await isMissingPermission(jsonResponse(403, {errorCode: 'not_admin'}))).toBe(true);
    });

    it('ignores other forbidden responses', async () => {
        expect(await isMissingPermission(jsonResponse(403, {errorCode: 'company_suspended'}))).toBe(false);
        expect(await isMissingPermission(new Response('Forbidden', {status: 403}))).toBe(false);
        expect(await isMissingPermission(new Response(null, {status: 403}))).toBe(false);
    });

    it('ignores the error code on any other status', async () => {
        expect(await isMissingPermission(jsonResponse(400, {errorCode: 'MISSING_PERMISSION'}))).toBe(false);
        expect(await isMissingPermission(jsonResponse(401, {errorCode: 'not_admin'}))).toBe(false);
    });

    it('ignores errors that are not responses', async () => {
        expect(await isMissingPermission(new TypeError('Failed to fetch'))).toBe(false);
        expect(await isMissingPermission(undefined)).toBe(false);
    });
});

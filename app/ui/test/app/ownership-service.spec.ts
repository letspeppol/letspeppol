import {afterEach, describe, expect, it, vi} from 'vitest';
import {OwnershipService, OwnershipSummary} from '../../src/services/app/ownership-service';

const STORAGE_KEY = 'accountOptions';

const ownership: OwnershipSummary = {
    peppolId: '0208:1234567890',
    companyName: 'Previous Account Company',
    type: 'ADMIN',
    peppolActive: true,
};

function createService(token: string) {
    let respond: (ownerships: OwnershipSummary[]) => void = () => undefined;
    const get = vi.fn(() => new Promise(resolve => {
        respond = ownerships => resolve({json: async () => ownerships});
    }));
    const service = Object.create(OwnershipService.prototype) as OwnershipService;
    Object.assign(service, {
        kycApi: {httpClient: {get}},
        currentToken: token,
        loadedToken: null,
        loadingPromise: null,
        ownerships: [],
    });
    return {service, respond: (ownerships: OwnershipSummary[]) => respond(ownerships)};
}

function tokenWith(claims: Record<string, unknown>): string {
    const encode = (value: unknown) => btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
    return `${encode({alg: 'none'})}.${encode(claims)}.signature`;
}

afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
});

describe('OwnershipService', () => {
    it('drops an ownership response that arrives after logout', async () => {
        const {service, respond} = createService('token-of-previous-account');

        const pendingLoad = service.loadOwnerships(true);
        service.onTokenChanged(null);
        respond([ownership]);

        expect(await pendingLoad).toEqual([]);
        expect(service.getCachedOwnerships()).toEqual([]);
        expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    });

    it('applies an ownership response while its token is still current', async () => {
        const {service, respond} = createService('current-token');

        const pendingLoad = service.loadOwnerships(true);
        respond([ownership]);

        expect(await pendingLoad).toEqual([ownership]);
        expect(service.getCachedOwnerships()).toEqual([ownership]);
        expect(JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')).toEqual([ownership]);
    });

    it('takes the permissions of a user from the token', () => {
        const {service} = createService('previous-token');

        service.onTokenChanged(tokenWith({peppolId: '0208:1234567890', accountType: 'USER', permissionMask: 7}));

        expect(service.permissionMask).toBe(7);
        expect(service.admin).toBe(false);
        expect(service.granted.INVOICE_SEND).toBe(true);
        expect(service.granted.PARTNER_MANAGE).toBe(false);
        expect(service.can(4)).toBe(true);
        expect(service.can(32)).toBe(false);
    });

    it('gives an administrator every permission when the token has no mask yet', () => {
        const {service} = createService('previous-token');

        service.onTokenChanged(tokenWith({peppolId: '0208:1234567890', accountType: 'ADMIN'}));

        expect(service.permissionMask).toBe(255);
        expect(service.admin).toBe(true);
        expect(service.granted.COMPANY_SETTINGS).toBe(true);
    });

    it('gives a user nothing when the token has no mask', () => {
        const {service} = createService('previous-token');

        service.onTokenChanged(tokenWith({peppolId: '0208:1234567890', accountType: 'USER'}));

        expect(service.permissionMask).toBe(0);
        expect(service.granted.INVOICE_READ).toBe(false);
    });

    it('does not treat an affiliate as administrator', () => {
        const {service} = createService('previous-token');

        service.onTokenChanged(tokenWith({peppolId: '0208:1234567890', accountType: 'AFFILIATE', permissionMask: 255}));

        expect(service.permissionMask).toBe(255);
        expect(service.admin).toBe(false);
    });

    it('drops every permission on logout', () => {
        const {service} = createService('previous-token');
        service.onTokenChanged(tokenWith({peppolId: '0208:1234567890', accountType: 'ADMIN', permissionMask: 255}));

        service.onTokenChanged(null);

        expect(service.permissionMask).toBe(0);
        expect(service.admin).toBe(false);
        expect(Object.values(service.granted)).not.toContain(true);
    });
});

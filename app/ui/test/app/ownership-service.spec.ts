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

afterEach(() => {
    localStorage.clear();
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
});

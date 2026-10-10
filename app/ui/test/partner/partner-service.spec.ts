import {describe, expect, it, vi} from 'vitest';
import {PartnerDto, PartnerService} from '../../src/services/app/partner-service';

interface PendingResponse {
    resolve: (partners: PartnerDto[]) => void;
}

const partner = (name: string): PartnerDto => ({
    name,
    peppolId: `0208:${name}`,
    customer: true,
    supplier: false,
});

function createService(initialPeppolId: string) {
    const actingCompany = {peppolId: initialPeppolId};
    const pending: PendingResponse[] = [];
    const get = vi.fn(() => new Promise(resolve => {
        pending.push({resolve: partners => resolve({json: async () => partners})});
    }));
    const service = Object.create(PartnerService.prototype) as PartnerService;
    Object.assign(service, {
        appApi: {httpClient: {get}},
        ownershipService: {getCurrentPeppolId: () => actingCompany.peppolId},
    });
    return {service, actingCompany, pending, get};
}

describe('PartnerService', () => {
    it('does not serve one company its previous company cached partners', async () => {
        const {service, actingCompany, pending, get} = createService('0208:A');

        const partnersOfA = service.getPartners();
        pending[0].resolve([partner('a-customer')]);
        expect(await partnersOfA).toEqual([partner('a-customer')]);

        actingCompany.peppolId = '0208:B';
        const partnersOfB = service.getPartners();
        expect(get).toHaveBeenCalledTimes(2);
        pending[1].resolve([partner('b-customer')]);
        expect(await partnersOfB).toEqual([partner('b-customer')]);
    });

    it('keeps a response that was in flight for the previous company out of the new cache', async () => {
        const {service, actingCompany, pending, get} = createService('0208:A');

        const partnersOfA = service.getPartners();
        actingCompany.peppolId = '0208:B';
        const partnersOfB = service.getPartners();
        expect(get).toHaveBeenCalledTimes(2);

        pending[1].resolve([partner('b-customer')]);
        pending[0].resolve([partner('a-customer')]);
        await Promise.all([partnersOfA, partnersOfB]);

        expect(await service.getPartners()).toEqual([partner('b-customer')]);
        expect(get).toHaveBeenCalledTimes(2);
    });

    it('does not let a request that was running when the cache was cleared refill it', async () => {
        const {service, pending, get} = createService('0208:A');

        const stalePartners = service.getPartners();
        service.clearCache();
        pending[0].resolve([partner('stale')]);
        await stalePartners;

        const freshPartners = service.getPartners();
        expect(get).toHaveBeenCalledTimes(2);
        pending[1].resolve([partner('fresh')]);
        expect(await freshPartners).toEqual([partner('fresh')]);
    });
});

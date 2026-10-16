import { describe, expect, it, vi } from 'vitest';

const cleared = vi.hoisted(() => ({
    calls: [] as string[],
    clearAccountCache: vi.fn(function (this: unknown) { cleared.calls.push('context'); }),
    clearCache: vi.fn(() => cleared.calls.push('partnerService')),
}));

vi.mock('@aurelia/kernel', async importOriginal => ({
    ...await importOriginal<typeof import('@aurelia/kernel')>(),
    resolve: () => cleared,
}));

import { AccountCacheService } from '../../src/services/app/account-cache-service';
import { PartnerContext } from '../../src/partner/partner-context';
import { ProductContext } from '../../src/product/product-context';

describe('AccountCacheService', () => {
    it('clears every company scoped store', () => {
        const service = new AccountCacheService();

        service.clearAll();

        expect(cleared.clearAccountCache).toHaveBeenCalledTimes(3);
        expect(cleared.clearCache).toHaveBeenCalledTimes(1);
    });
});

describe('PartnerContext', () => {
    it('drops the partner list on an account switch', () => {
        const context = new PartnerContext();
        context.partners = [{name: 'Acme'} as never];
        context.filteredPartners = [{name: 'Acme'} as never];
        context.selectedPartner = {name: 'Acme'} as never;

        context.clearAccountCache();

        expect(context.partners).toEqual([]);
        expect(context.filteredPartners).toEqual([]);
        expect(context.selectedPartner).toBeUndefined();
    });
});

describe('ProductContext', () => {
    it('drops the product list on an account switch so it is fetched again', () => {
        const context = new ProductContext();
        context.products = [{name: 'Widget'} as never];
        context.productCategories = [{id: 1, name: 'Tools'} as never];
        context.productCategoryMap.set(1, {id: 1, name: 'Tools'});
        context.selectedProduct = {name: 'Widget'} as never;

        context.clearAccountCache();

        expect(context.products).toBeUndefined();
        expect(context.productCategories).toEqual([]);
        expect(context.productCategoryMap.size).toBe(0);
        expect(context.selectedProduct).toBeUndefined();
    });
});

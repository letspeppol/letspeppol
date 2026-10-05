import {describe, expect, it, vi} from 'vitest';
import type {IRouteViewModel, RouteNode} from '@aurelia/router';
import {CompanyPermissionHook, CompanyRouteData} from '../../src/app/company-permission-hook';
import {CompanyPermission, hasPermission} from '../../src/services/app/company-permission';

const PEPPOL_ID = '0208:0123456789';

function createHook(permissionMask: number, admin = false) {
    const publish = vi.fn();
    const hook = Object.create(CompanyPermissionHook.prototype) as CompanyPermissionHook;
    Object.assign(hook, {
        ownershipService: {
            admin,
            can: (permission: CompanyPermission) => hasPermission(permissionMask, permission),
            getCurrentPeppolId: () => PEPPOL_ID,
        },
        ea: {publish},
        i18n: {tr: (key: string) => key},
    });
    const canLoad = (data: CompanyRouteData | undefined, params: Record<string, string> = {peppolId: PEPPOL_ID}) =>
        hook.canLoad({} as IRouteViewModel, params, {data} as unknown as RouteNode);
    return {canLoad, publish};
}

describe('CompanyPermissionHook', () => {
    it('leaves public routes alone', () => {
        const {canLoad, publish} = createHook(0);

        expect(canLoad({allowEveryone: true, adminOnly: true})).toBe(true);
        expect(publish).not.toHaveBeenCalled();
    });

    it('lets every member open a route without requirements', () => {
        const {canLoad} = createHook(0);

        expect(canLoad({})).toBe(true);
        expect(canLoad(undefined)).toBe(true);
    });

    it('opens a route when one of the required permissions is granted', () => {
        const {canLoad} = createHook(CompanyPermission.PARTNER_MANAGE);

        expect(canLoad({requiresAny: [CompanyPermission.INVOICE_READ, CompanyPermission.PARTNER_MANAGE]})).toBe(true);
    });

    it('sends a user without the permission back to the dashboard with a warning', () => {
        const {canLoad, publish} = createHook(CompanyPermission.INVOICE_READ);

        expect(canLoad({requiresAny: [CompanyPermission.INVOICE_EXPORT]})).toBe(`/${PEPPOL_ID}/dashboard`);
        expect(publish).toHaveBeenCalledWith('alert', {alertType: 'Warning', text: 'alert.permission.route-denied'});
    });

    it('keeps admin-only routes for the administrator', () => {
        expect(createHook(255, false).canLoad({adminOnly: true})).toBe(`/${PEPPOL_ID}/dashboard`);
        expect(createHook(255, true).canLoad({adminOnly: true})).toBe(true);
    });

    it('falls back to the acting company when the route carries no Peppol ID', () => {
        const {canLoad} = createHook(0);

        expect(canLoad({adminOnly: true}, {})).toBe(`/${PEPPOL_ID}/dashboard`);
    });
});

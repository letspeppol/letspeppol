import {describe, expect, it, vi} from 'vitest';
import {Params, RouteNode} from '@aurelia/router';
import {AuthenticationHook} from '../../src/app/authentication-hook';

const HOME_ROUTE = '/0208:1234567890/dashboard';

function createHook({restored = true, permissions = [] as string[]} = {}) {
    const loginService = {
        ensureAuthenticated: vi.fn(async () => restored),
        rememberCurrentNavigation: vi.fn(),
        getHomeRoute: vi.fn(() => HOME_ROUTE),
    };
    const ownershipService = {
        hasPermission: vi.fn((permission: string) => permissions.includes(permission)),
    };
    const hook = Object.create(AuthenticationHook.prototype) as AuthenticationHook;
    Object.assign(hook, {loginService, ownershipService});
    return {hook, loginService};
}

const backofficeRoute = {data: {permission: 'REVIEW_REGISTRATIONS'}} as unknown as RouteNode;
const companyRoute = {data: {}} as unknown as RouteNode;

describe('AuthenticationHook', () => {
    it('sends a user without the required permission to their home route', async () => {
        const {hook} = createHook();

        expect(await hook.canLoad(undefined as never, {} as Params, backofficeRoute)).toBe(HOME_ROUTE);
    });

    it('opens a permission route for a user holding that permission', async () => {
        const {hook} = createHook({permissions: ['REVIEW_REGISTRATIONS']});

        expect(await hook.canLoad(undefined as never, {} as Params, backofficeRoute)).toBe(true);
    });

    it('asks an unauthenticated visitor of a permission route to log in first', async () => {
        const {hook, loginService} = createHook({restored: false, permissions: ['REVIEW_REGISTRATIONS']});

        expect(await hook.canLoad(undefined as never, {} as Params, backofficeRoute)).toBe('/login');
        expect(loginService.rememberCurrentNavigation).toHaveBeenCalled();
    });

    it('keeps company routes open for the requested company', async () => {
        const {hook} = createHook();

        expect(await hook.canLoad(undefined as never, {peppolId: '0208:1234567890'} as Params, companyRoute)).toBe(true);
    });
});

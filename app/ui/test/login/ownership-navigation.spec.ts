import {afterEach, describe, expect, it, vi} from 'vitest';
import {IRouteViewModel, RouteNode} from '@aurelia/router';
import {AuthenticationHook} from '../../src/app/authentication-hook';
import {LoginService} from '../../src/services/app/login-service';
import {peekPendingNavigation, rememberCurrentNavigation} from '../../src/services/app/ownership-route';

const DEFAULT_PEPPOL_ID = '0208:0123456789';
const STALE_PEPPOL_ID = '0208:9999999999';
const DEFAULT_DASHBOARD = `/${DEFAULT_PEPPOL_ID}/dashboard`;

function createLoginService(peppolId: string | null = DEFAULT_PEPPOL_ID) {
    const service = Object.create(LoginService.prototype) as LoginService;
    Object.assign(service, {
        accessToken: null,
        ownershipService: {
            getCurrentPeppolId: () => peppolId,
            getRememberedOwnershipType: () => null,
        },
    });
    return service;
}

function hookFor(loginService: LoginService) {
    const hook = Object.create(AuthenticationHook.prototype) as AuthenticationHook;
    Object.assign(loginService, {ensureAuthenticated: vi.fn().mockResolvedValue(true)});
    Object.assign(hook, {loginService});
    return hook;
}

afterEach(() => {
    sessionStorage.clear();
    history.replaceState({}, '', '/');
});

describe('ownership navigation', () => {
    it('keeps a deep link when the token selects the requested company', () => {
        history.replaceState({}, '', `/${DEFAULT_PEPPOL_ID}/invoices/17?box=sent#details`);
        rememberCurrentNavigation();

        expect(createLoginService().getPostLoginPath())
            .toBe(`/${DEFAULT_PEPPOL_ID}/invoices/17?box=sent#details`);
        expect(peekPendingNavigation()).toBeNull();
    });

    it('discards a stale company deep link after login selects the default company', () => {
        history.replaceState({}, '', `/${STALE_PEPPOL_ID}/invoices/17?box=sent#details`);
        rememberCurrentNavigation();

        expect(createLoginService().getPostLoginPath()).toBe(DEFAULT_DASHBOARD);
        expect(peekPendingNavigation()).toBeNull();
    });

    it('recognizes successful authorization when the server selects another company', async () => {
        const service = createLoginService();
        const silentLogin = vi.spyOn(service, 'silentLogin').mockResolvedValue({authorized: true});

        expect(await service.ensureAuthenticated(STALE_PEPPOL_ID)).toBe(true);
        expect(silentLogin).toHaveBeenCalledWith({peppolId: STALE_PEPPOL_ID, type: undefined});
    });

    it.each([
        [STALE_PEPPOL_ID, DEFAULT_DASHBOARD],
        [DEFAULT_PEPPOL_ID, true],
        [undefined, DEFAULT_DASHBOARD],
    ])('routes company %s to %s after successful authorization', async (peppolId, expected) => {
        const hook = Object.create(AuthenticationHook.prototype) as AuthenticationHook;
        Object.assign(hook, {
            loginService: {
                ensureAuthenticated: vi.fn().mockResolvedValue(true),
                getCurrentOwnershipRoute: () => DEFAULT_DASHBOARD,
            },
        });

        expect(await hook.canLoad({} as IRouteViewModel, {peppolId}, {} as RouteNode)).toBe(expected);
    });

    it('lands an account without a company on its account page after login', () => {
        history.replaceState({}, '', `/${STALE_PEPPOL_ID}/invoices/17`);
        rememberCurrentNavigation();

        expect(createLoginService(null).getPostLoginPath()).toBe('/account');
    });

    it('keeps an account without a company on the add-company screen it asked for', () => {
        history.replaceState({}, '', '/add-ownership');
        rememberCurrentNavigation();

        expect(createLoginService(null).getPostLoginPath()).toBe('/add-ownership');
    });

    it('lets an account without a company open its account page', async () => {
        const hook = hookFor(createLoginService(null));

        expect(await hook.canLoad({} as IRouteViewModel, {}, {data: {withoutCompany: '/account'}} as unknown as RouteNode))
            .toBe(true);
    });

    it('sends an account without a company from company screens to its account page', async () => {
        const hook = hookFor(createLoginService(null));

        expect(await hook.canLoad({} as IRouteViewModel, {peppolId: STALE_PEPPOL_ID}, {data: {}} as unknown as RouteNode))
            .toBe('/account');
        expect(await hook.canLoad({} as IRouteViewModel, {}, {data: {}} as unknown as RouteNode))
            .toBe('/account');
    });

    it('sends a company member from the account page without company to the one of its company', async () => {
        const hook = hookFor(createLoginService());

        expect(await hook.canLoad({} as IRouteViewModel, {}, {data: {withoutCompany: '/account'}} as unknown as RouteNode))
            .toBe(`/${DEFAULT_PEPPOL_ID}/account`);
    });

    it('continues to send failed authorization to login', async () => {
        const hook = Object.create(AuthenticationHook.prototype) as AuthenticationHook;
        const rememberNavigation = vi.fn();
        Object.assign(hook, {
            loginService: {
                ensureAuthenticated: vi.fn().mockResolvedValue(false),
                rememberCurrentNavigation: rememberNavigation,
            },
        });

        expect(await hook.canLoad({} as IRouteViewModel, {peppolId: STALE_PEPPOL_ID}, {} as RouteNode))
            .toBe('/login');
        expect(rememberNavigation).toHaveBeenCalledOnce();
    });
});

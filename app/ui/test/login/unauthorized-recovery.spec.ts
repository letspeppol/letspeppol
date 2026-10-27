import {afterEach, describe, expect, it, vi} from 'vitest';
import {LoginService} from '../../src/services/app/login-service';
import {peekPendingNavigation} from '../../src/services/app/ownership-route';

const PEPPOL_ID = '0208:0123456789';
const OTHER_PEPPOL_ID = '0208:9999999999';

function createLoginService(renewed: boolean, peppolIdAfterRenewal = PEPPOL_ID) {
    let peppolId = PEPPOL_ID;
    const service = Object.create(LoginService.prototype) as LoginService;
    const router = {load: vi.fn().mockResolvedValue(true)};
    Object.assign(service, {
        accessToken: 'revoked-token',
        router,
        ownershipService: {
            getCurrentPeppolId: () => peppolId,
        },
    });
    const refreshToken = vi.spyOn(service, 'refreshToken').mockImplementation(async () => {
        if (renewed) {
            peppolId = peppolIdAfterRenewal;
            Object.assign(service, {accessToken: 'renewed-token'});
        }
        return renewed;
    });
    const logout = vi.spyOn(service, 'logout').mockImplementation(() => {
        Object.assign(service, {accessToken: null});
    });
    return {service, router, refreshToken, logout};
}

afterEach(() => {
    sessionStorage.clear();
    history.replaceState({}, '', '/');
});

describe('recovering from a 401', () => {
    it('renews the token silently and stays on the page', async () => {
        const {service, router, logout} = createLoginService(true);

        await service.recoverFromUnauthorized('Bearer revoked-token');

        expect(logout).not.toHaveBeenCalled();
        expect(router.load).not.toHaveBeenCalled();
    });

    it('opens the dashboard of the company the server selected instead', async () => {
        const {service, router, logout} = createLoginService(true, OTHER_PEPPOL_ID);

        await service.recoverFromUnauthorized('Bearer revoked-token');

        expect(logout).not.toHaveBeenCalled();
        expect(router.load).toHaveBeenCalledWith(`/${OTHER_PEPPOL_ID}/dashboard`);
    });

    it('signs out locally and asks to log in when the session cannot be renewed', async () => {
        history.replaceState({}, '', `/${PEPPOL_ID}/invoices`);
        const {service, router, logout} = createLoginService(false);

        await service.recoverFromUnauthorized('Bearer revoked-token');

        expect(logout).toHaveBeenCalledWith(false);
        expect(router.load).toHaveBeenCalledWith('/login');
        expect(peekPendingNavigation()).toBe(`/${PEPPOL_ID}/invoices`);
    });

    it('ignores a 401 for a token that was already replaced', async () => {
        const {service, router, refreshToken} = createLoginService(true);

        await service.recoverFromUnauthorized('Bearer older-token');

        expect(refreshToken).not.toHaveBeenCalled();
        expect(router.load).not.toHaveBeenCalled();
    });
});

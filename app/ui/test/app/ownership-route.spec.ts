import {afterEach, describe, expect, it} from 'vitest';
import {
    clearRememberedOwnership,
    consumePendingNavigation,
    getPeppolIdFromPath,
    getRememberedOwnershipType,
    ownershipRoute,
    peekPendingNavigation,
    rememberActingOwnership,
    rememberCurrentNavigation,
} from '../../src/services/app/ownership-route';

afterEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    history.replaceState({}, '', '/');
});

describe('ownership routes', () => {
    it('puts the active Peppol ID before every protected screen', () => {
        expect(ownershipRoute('0208:0123456789', '/invoices/17'))
            .toBe('/0208:0123456789/invoices/17');
        expect(getPeppolIdFromPath('/0208:0123456789/invoices/17'))
            .toBe('0208:0123456789');
    });

    it('does not treat context-free or public routes as ownership routes', () => {
        expect(getPeppolIdFromPath('/dashboard')).toBeNull();
        expect(getPeppolIdFromPath('/login')).toBeNull();
        expect(getPeppolIdFromPath('/callback?code=abc')).toBeNull();
    });

    it('keeps a bookmarked deep link through the login callback', () => {
        history.replaceState({}, '', '/0208:0123456789/invoices/17?box=sent#details');
        rememberCurrentNavigation();

        expect(peekPendingNavigation())
            .toBe('/0208:0123456789/invoices/17?box=sent#details');
        expect(consumePendingNavigation())
            .toBe('/0208:0123456789/invoices/17?box=sent#details');
        expect(peekPendingNavigation()).toBeNull();
    });

    it('stores the acting role per tab instead of shared local storage', () => {
        rememberActingOwnership('0208:0123456789', 'ADMIN');

        expect(getRememberedOwnershipType('0208:0123456789')).toBe('ADMIN');
        expect(getRememberedOwnershipType('0208:9999999999')).toBeNull();
        expect(localStorage.length).toBe(0);

        clearRememberedOwnership();
        expect(getRememberedOwnershipType('0208:0123456789')).toBeNull();
    });
});

import {beforeEach, describe, expect, test} from 'vitest';
import {
    getAutomaticVatDisplayMode,
    getVatDisplayMode,
    LocalStorageVatDisplay,
} from '../../src/services/app/vat-display-service';

describe('VAT display mode helpers', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    test('always shows amounts including VAT for companies without a VAT number', () => {
        expect(getVatDisplayMode(undefined, 'excl')).toBe('incl');
        expect(getVatDisplayMode('', 'excl')).toBe('incl');
        expect(getVatDisplayMode('   ', 'excl')).toBe('incl');
    });

    test('uses the saved preference for companies with a VAT number', () => {
        expect(getVatDisplayMode('BE0123456789', 'incl')).toBe('incl');
        expect(getVatDisplayMode('BE0123456789', 'excl')).toBe('excl');
    });

    test('keeps existing VAT users on excluding VAT by default until they choose otherwise', () => {
        expect(getAutomaticVatDisplayMode('BE0123456789')).toBe('excl');
        expect(new LocalStorageVatDisplay().mode).toBe('excl');
    });
});

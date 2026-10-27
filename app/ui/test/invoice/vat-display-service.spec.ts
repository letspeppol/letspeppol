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

describe('VAT display preference per account', () => {
    beforeEach(() => {
        localStorage.clear();
    });

    test('each account keeps its own choice in the same browser', () => {
        const vatDisplay = new LocalStorageVatDisplay();
        vatDisplay.useAccount('accountant');
        vatDisplay.setMode('incl');

        vatDisplay.useAccount('director');
        expect(vatDisplay.mode).toBe('excl');
        vatDisplay.setMode('excl');

        vatDisplay.useAccount('accountant');
        expect(vatDisplay.mode).toBe('incl');
    });

    test('an account without its own choice keeps the choice saved before preferences were per account', () => {
        localStorage.setItem('lp.vatDisplay.v1', 'incl');
        const vatDisplay = new LocalStorageVatDisplay();

        vatDisplay.useAccount('director');

        expect(vatDisplay.mode).toBe('incl');
    });

    test('switching account tells the screens showing totals', () => {
        const vatDisplay = new LocalStorageVatDisplay();
        vatDisplay.useAccount('accountant');
        vatDisplay.setMode('incl');
        vatDisplay.useAccount(null);
        const modes: string[] = [];
        vatDisplay.subscribe(mode => modes.push(mode));

        vatDisplay.useAccount('accountant');

        expect(modes).toEqual(['incl']);
    });
});

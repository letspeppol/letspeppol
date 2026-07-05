import { describe, expect, it } from 'vitest';
import { normalizeBelgianStructuredCommunication } from '../../src/invoice/belgian-structured-communication';

describe('normalizeBelgianStructuredCommunication', () => {
    it('normalizes valid Belgian structured communication values to 12 digits', () => {
        expect(normalizeBelgianStructuredCommunication('+++123/4567/89002+++')).toBe('123456789002');
        expect(normalizeBelgianStructuredCommunication('123456789002')).toBe('123456789002');
        expect(normalizeBelgianStructuredCommunication('123 4567 89002')).toBe('123456789002');
        expect(normalizeBelgianStructuredCommunication('000-0000-00097')).toBe('000000000097');
    });

    it('rejects values with invalid check digits', () => {
        expect(normalizeBelgianStructuredCommunication('+++123/4567/89012+++')).toBe('');
    });

    it('rejects non Belgian structured communication values', () => {
        expect(normalizeBelgianStructuredCommunication('INV-2026-0001')).toBe('');
        expect(normalizeBelgianStructuredCommunication('12345678900')).toBe('');
        expect(normalizeBelgianStructuredCommunication('1234567890023')).toBe('');
        expect(normalizeBelgianStructuredCommunication(null)).toBe('');
        expect(normalizeBelgianStructuredCommunication(undefined)).toBe('');
    });
});

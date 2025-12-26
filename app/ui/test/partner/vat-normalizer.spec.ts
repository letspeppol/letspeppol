import { describe, it, expect } from 'vitest';
import { normalizeVatNumber } from '../../src/partner/vat-normalizer';

describe('normalizeVatNumber', () => {
  it('strips dots/spaces and uppercases country code', () => {
    expect(normalizeVatNumber(' be 0123.456.789 ').normalized).toBe('BE0123456789');
  });

  it('removes non-digits after the first two letters', () => {
    expect(normalizeVatNumber('BE 01A23-45/6x').normalized).toBe('BE0123456');
  });

  it('keeps all digits (including leading zeros) when there is no 2-letter prefix', () => {
    expect(normalizeVatNumber('1B.234').normalized).toBe('1234');
  });

  it('keeps leading zeros when normalizing digit-only VATs with separators', () => {
    expect(normalizeVatNumber('0732.789.832').normalized).toBe('0732789832');
  });

  it('handles empty/null safely', () => {
    expect(normalizeVatNumber('').normalized).toBe('');
    expect(normalizeVatNumber(undefined).normalized).toBe('');
    expect(normalizeVatNumber(null).normalized).toBe('');
  });
});

import { describe, expect, it } from 'vitest';
import {
    calculateDueDate,
    candidateTerms,
    formatPaymentTermCode,
    parsePaymentTermCode,
    preferredTerm,
} from '../../src/services/app/payment-terms';

describe('payment term codes', () => {
    it('round-trips every shape', () => {
        for (const code of ['NET_0', 'NET_30', 'EOM_0', 'EOM_30', 'EONM_0', 'EONM_45']) {
            expect(formatPaymentTermCode(parsePaymentTermCode(code))).toBe(code);
        }
    });

    it('reads the codes stored before terms became days-after-basis', () => {
        expect(parsePaymentTermCode('15_DAYS')).toEqual({days: 15, basis: 'INVOICE_DATE'});
        expect(parsePaymentTermCode('30_DAYS')).toEqual({days: 30, basis: 'INVOICE_DATE'});
        expect(parsePaymentTermCode('60_DAYS')).toEqual({days: 60, basis: 'INVOICE_DATE'});
        expect(parsePaymentTermCode('END_OF_NEXT_MONTH')).toEqual({days: 0, basis: 'END_OF_NEXT_MONTH'});
    });

    it('keeps the due dates the legacy codes produced', () => {
        expect(calculateDueDate(parsePaymentTermCode('15_DAYS'), '2026-07-05')).toBe('2026-07-20');
        expect(calculateDueDate(parsePaymentTermCode('30_DAYS'), '2026-07-05')).toBe('2026-08-04');
        expect(calculateDueDate(parsePaymentTermCode('60_DAYS'), '2026-07-05')).toBe('2026-09-03');
        expect(calculateDueDate(parsePaymentTermCode('END_OF_NEXT_MONTH'), '2026-07-05')).toBe('2026-08-31');
    });

    it('rejects codes it does not understand', () => {
        for (const code of [undefined, '', 'NET', 'NET_-5', 'NET_999', 'WEEKS_2', '30 days']) {
            expect(parsePaymentTermCode(code)).toBeUndefined();
        }
    });
});

describe('calculateDueDate', () => {
    it('counts days from the invoice date', () => {
        expect(calculateDueDate({days: 30, basis: 'INVOICE_DATE'}, '2026-07-05')).toBe('2026-08-04');
        expect(calculateDueDate({days: 0, basis: 'INVOICE_DATE'}, '2026-07-05')).toBe('2026-07-05');
    });

    it('counts days from the end of the invoice month', () => {
        expect(calculateDueDate({days: 0, basis: 'END_OF_MONTH'}, '2026-07-05')).toBe('2026-07-31');
        expect(calculateDueDate({days: 30, basis: 'END_OF_MONTH'}, '2026-07-05')).toBe('2026-08-30');
    });

    it('counts days from the end of the month after the invoice month', () => {
        expect(calculateDueDate({days: 0, basis: 'END_OF_NEXT_MONTH'}, '2026-07-05')).toBe('2026-08-31');
        expect(calculateDueDate({days: 10, basis: 'END_OF_NEXT_MONTH'}, '2026-01-31')).toBe('2026-03-10');
    });

    it('handles short months and leap years', () => {
        expect(calculateDueDate({days: 0, basis: 'END_OF_MONTH'}, '2026-02-10')).toBe('2026-02-28');
        expect(calculateDueDate({days: 0, basis: 'END_OF_MONTH'}, '2028-02-10')).toBe('2028-02-29');
        expect(calculateDueDate({days: 0, basis: 'END_OF_NEXT_MONTH'}, '2026-01-31')).toBe('2026-02-28');
    });

    it('returns nothing for an unusable issue date', () => {
        expect(calculateDueDate({days: 30, basis: 'INVOICE_DATE'}, 'not-a-date')).toBeUndefined();
    });
});

describe('candidateTerms', () => {
    it('finds every term landing on the due date, including across the month boundary', () => {
        expect(candidateTerms('2026-07-05', '2026-08-30')).toEqual([
            {days: 30, basis: 'END_OF_MONTH'},
            {days: 56, basis: 'INVOICE_DATE'},
        ]);
    });

    it('drops bases the due date falls before', () => {
        expect(candidateTerms('2026-07-05', '2026-07-10')).toEqual([{days: 5, basis: 'INVOICE_DATE'}]);
    });

    it('returns nothing for unusable dates', () => {
        expect(candidateTerms('2026-07-05', 'nonsense')).toEqual([]);
        expect(candidateTerms('2026-07-05', '2026-07-04')).toEqual([]);
    });
});

describe('preferredTerm', () => {
    it('describes a month-end due date as end of month', () => {
        expect(preferredTerm('2026-07-05', '2026-07-31')).toEqual({days: 0, basis: 'END_OF_MONTH'});
        expect(preferredTerm('2026-07-05', '2026-08-31')).toEqual({days: 0, basis: 'END_OF_NEXT_MONTH'});
    });

    it('describes everything else as a day count from the invoice date', () => {
        expect(preferredTerm('2026-07-05', '2026-08-04')).toEqual({days: 30, basis: 'INVOICE_DATE'});
        expect(preferredTerm('2026-07-05', '2026-08-30')).toEqual({days: 56, basis: 'INVOICE_DATE'});
    });

    it('returns nothing when no term reaches the due date', () => {
        expect(preferredTerm('2026-07-05', '2026-07-04')).toBeUndefined();
    });
});

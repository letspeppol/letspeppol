import moment from "moment";

export type PaymentTermBasis = 'INVOICE_DATE' | 'END_OF_MONTH' | 'END_OF_NEXT_MONTH';

export interface PaymentTerm {
    days: number;
    basis: PaymentTermBasis;
}

export const PAYMENT_TERM_BASES: PaymentTermBasis[] = ['INVOICE_DATE', 'END_OF_MONTH', 'END_OF_NEXT_MONTH'];

export const DEFAULT_PAYMENT_TERM: PaymentTerm = {days: 30, basis: 'INVOICE_DATE'};

export const MAX_PAYMENT_TERM_DAYS = 365;

const BASIS_PREFIX: Record<PaymentTermBasis, string> = {
    INVOICE_DATE: 'NET',
    END_OF_MONTH: 'EOM',
    END_OF_NEXT_MONTH: 'EONM',
};

const CODE_PATTERN = /^(NET|EOM|EONM)_(\d{1,3})$/;

const LEGACY_CODES: Record<string, PaymentTerm> = {
    '15_DAYS': {days: 15, basis: 'INVOICE_DATE'},
    '30_DAYS': {days: 30, basis: 'INVOICE_DATE'},
    '60_DAYS': {days: 60, basis: 'INVOICE_DATE'},
    'END_OF_NEXT_MONTH': {days: 0, basis: 'END_OF_NEXT_MONTH'},
};

export function formatPaymentTermCode(term: PaymentTerm): string {
    return `${BASIS_PREFIX[term.basis]}_${term.days}`;
}

export function parsePaymentTermCode(code?: string): PaymentTerm | undefined {
    if (!code) {
        return undefined;
    }
    const legacy = LEGACY_CODES[code];
    if (legacy) {
        return {...legacy};
    }
    const match = CODE_PATTERN.exec(code);
    if (!match) {
        return undefined;
    }
    const basis = PAYMENT_TERM_BASES.find(candidate => BASIS_PREFIX[candidate] === match[1]);
    const days = Number(match[2]);
    if (!basis || days > MAX_PAYMENT_TERM_DAYS) {
        return undefined;
    }
    return {days, basis};
}

function anchorDate(basis: PaymentTermBasis, issueDate: string) {
    const date = moment(issueDate, 'YYYY-MM-DD', true);
    switch (basis) {
        case 'END_OF_MONTH':
            return date.endOf('month').startOf('day');
        case 'END_OF_NEXT_MONTH':
            return date.add(1, 'month').endOf('month').startOf('day');
        default:
            return date;
    }
}

export function calculateDueDate(term: PaymentTerm, issueDate: string): string | undefined {
    const anchor = anchorDate(term.basis, issueDate);
    if (!anchor.isValid()) {
        return undefined;
    }
    return anchor.add(term.days, 'day').format('YYYY-MM-DD');
}

export function candidateTerms(issueDate: string, dueDate: string): PaymentTerm[] {
    const due = moment(dueDate, 'YYYY-MM-DD', true);
    if (!due.isValid() || !moment(issueDate, 'YYYY-MM-DD', true).isValid()) {
        return [];
    }
    return PAYMENT_TERM_BASES
        .map(basis => ({basis, days: due.diff(anchorDate(basis, issueDate), 'day')}))
        .filter(term => term.days >= 0 && term.days <= MAX_PAYMENT_TERM_DAYS)
        .sort((left, right) => left.days - right.days);
}

export function preferredTerm(issueDate: string, dueDate: string): PaymentTerm | undefined {
    const candidates = candidateTerms(issueDate, dueDate);
    return candidates.find(term => term.days === 0 && term.basis !== 'INVOICE_DATE')
        ?? candidates.find(term => term.basis === 'INVOICE_DATE');
}

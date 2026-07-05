const BELGIAN_STRUCTURED_COMMUNICATION_LENGTH = 12;
const BELGIAN_STRUCTURED_COMMUNICATION_BASE_LENGTH = 10;
const BELGIAN_STRUCTURED_COMMUNICATION_MODULO = 97;

export function normalizeBelgianStructuredCommunication(value: unknown): string {
    if (typeof value !== 'string') {
        return '';
    }

    const trimmed = value.trim();

    if (!trimmed || !/^[\d\s+/.()-]+$/.test(trimmed)) {
        return '';
    }

    const digits = trimmed.replace(/\D/g, '');

    if (digits.length !== BELGIAN_STRUCTURED_COMMUNICATION_LENGTH) {
        return '';
    }

    const baseNumber = Number(digits.slice(0, BELGIAN_STRUCTURED_COMMUNICATION_BASE_LENGTH));
    const checkDigits = Number(digits.slice(BELGIAN_STRUCTURED_COMMUNICATION_BASE_LENGTH));
    const modulo = baseNumber % BELGIAN_STRUCTURED_COMMUNICATION_MODULO;
    const expectedCheckDigits = modulo === 0 ? BELGIAN_STRUCTURED_COMMUNICATION_MODULO : modulo;

    return checkDigits === expectedCheckDigits ? digits : '';
}

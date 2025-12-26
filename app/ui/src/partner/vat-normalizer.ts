export interface VatNormalizationResult {
  /** Canonical VAT number (e.g. "BE0123456789"). Empty string when nothing usable. */
  normalized: string;
  /** True if the normalized VAT is in the shape: 2 letters + at least 1 digit. */
  isValidShape: boolean;
}

/**
 * Normalizes VAT input.
 *
 * Rules:
 * - Remove whitespace, dots and any non-alphanumeric characters.
 * - The first two characters may be letters (country code). They are uppercased.
 * - All remaining characters must be digits; non-digits are removed.
 *
 * This is intentionally a best-effort normalizer for UI input. It does not verify
 * country-specific VAT length/checksum.
 */
export function normalizeVatNumber(input: string | null | undefined): VatNormalizationResult {
  if (!input) {
    return { normalized: '', isValidShape: false };
  }

  // Remove whitespace, dots and any other non-alphanumeric characters.
  const compact = input.replace(/[^0-9a-z]/gi, '');
  if (compact.length === 0) {
    return { normalized: '', isValidShape: false };
  }

  // Country code is only recognized when the *first two* characters are letters.
  const prefix = compact.slice(0, 2).toUpperCase();
  const hasTwoLetters = /^[A-Z]{2}$/.test(prefix);

  // Keep all digits (preserve leading zeros). When a country code is present,
  // digits start after the 2-letter prefix; otherwise digits come from the full input.
  const digitsSource = hasTwoLetters ? compact.slice(2) : compact;
  const digits = digitsSource.replace(/\D/g, '');

  const normalized = hasTwoLetters ? `${prefix}${digits}` : digits;
  const isValidShape = hasTwoLetters && digits.length > 0;

  return { normalized, isValidShape };
}

import type {ClassifiedTaxCategory} from "../peppol/ubl";

export const VAT_RULESET_OPTIONS = [
    'VAT_REGISTERED',
    'VAT_EXEMPT_ART_56BIS',
    'VAT_EXEMPT_ART_44',
 ] as const;
export type VatRuleset = typeof VAT_RULESET_OPTIONS[number];

export const ZERO_VAT_REASON_OPTIONS = [
    'E',
    'K',
    'G',
    'AE',
    'Z',
    'O',
] as const;
export type ZeroVatReasonId = typeof ZERO_VAT_REASON_OPTIONS[number];

export function isVatExemptRuleset(vatRuleset?: VatRuleset): boolean {
    return vatRuleset === 'VAT_EXEMPT_ART_56BIS' || vatRuleset === 'VAT_EXEMPT_ART_44';
}

export function getVatRulesetLabelKey(vatRuleset: VatRuleset | undefined): string {
    return `account.vat-ruleset.options.${vatRuleset ?? VAT_RULESET_OPTIONS[0]}`;
}

export function getVatRulesetLabel(vatRuleset: VatRuleset | undefined, translate: (key: string) => string): string {
    return translate(getVatRulesetLabelKey(vatRuleset));
}

export function getZeroVatReasonLabelKey(reasonId: ZeroVatReasonId | undefined): string {
    return `invoice.zero-vat-reason.options.${reasonId ?? ZERO_VAT_REASON_OPTIONS[0]}`;
}

export function getZeroVatReasonLabel(reasonId: ZeroVatReasonId | undefined, translate: (key: string) => string): string {
    return translate(getZeroVatReasonLabelKey(reasonId));
}

export function getZeroVatReasonCode(reasonId: ZeroVatReasonId | undefined): string | undefined {
    switch (reasonId) {
        case 'AE':
            return 'VATEX-EU-AE';
        case 'K':
            return 'VATEX-EU-IC';
        case 'G':
            return 'VATEX-EU-G';
        case 'O':
            return 'VATEX-EU-O';
        default:
            return undefined;
    }
}

export function createZeroVatCategory(reasonId: ZeroVatReasonId): ClassifiedTaxCategory {
    return {
        ID: reasonId,
        Percent: 0,
        TaxExemptionReasonCode: getZeroVatReasonCode(reasonId),
        TaxScheme: { ID: 'VAT' }
    };
}

export function getVatRulesetExemptionReason(vatRuleset: VatRuleset | undefined, translate: (key: string) => string): string | undefined {
    if (!isVatExemptRuleset(vatRuleset)) {
        return undefined;
    }
    return getVatRulesetLabel(vatRuleset, translate);
}

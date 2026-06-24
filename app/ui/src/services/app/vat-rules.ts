import {getLines, type ClassifiedTaxCategory, type UBLDoc} from "../peppol/ubl";

export const VAT_RULESET_OPTIONS = [
    'VAT_REGISTERED',
    'VAT_EXEMPT_ART_56BIS',
 ] as const;
export type VatRuleset = typeof VAT_RULESET_OPTIONS[number];

export const ZERO_VAT_REASON_OPTIONS = [
    'E',
    'K',
    'G',
    'AE',
    'Z',
] as const;
export type ZeroVatReasonId = typeof ZERO_VAT_REASON_OPTIONS[number];

export interface VatReasonSelectionPayload {
    selectedTaxCategoryId: string;
    writtenReason: string;
}

export function isVatExemptRuleset(vatRuleset?: VatRuleset): boolean {
    return vatRuleset === 'VAT_EXEMPT_ART_56BIS';
}

export function getZeroVatReasonLabelKey(reasonId: ZeroVatReasonId | undefined): string {
    return `invoice.zero-vat-reason.options.${reasonId ?? ZERO_VAT_REASON_OPTIONS[0]}`;
}

export function getZeroVatReasonCode(reasonId: ZeroVatReasonId | undefined): string | undefined {
    switch (reasonId) {
        case 'AE':
            return 'VATEX-EU-AE';
        case 'K':
            return 'VATEX-EU-IC';
        case 'G':
            return 'VATEX-EU-G';
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

export function createNotSubjectToVatCategory(): ClassifiedTaxCategory {
    return {
        ID: 'O',
        Percent: undefined,
        TaxScheme: { ID: 'VAT' }
    };
}

export function createVatExemptCategory(reason: string): ClassifiedTaxCategory {
    return {
        ID: 'E',
        Percent: 0,
        TaxExemptionReason: reason,
        TaxScheme: { ID: 'VAT' }
    };
}

export function requiresDeliveryDetails(reasonId: ZeroVatReasonId | string | undefined): boolean {
    return reasonId === 'K';
}

export function collectVatReasonSelections(doc: UBLDoc | undefined): VatReasonSelectionPayload[] {
    return (getLines(doc) ?? [])
        .map(line => ({
            selectedTaxCategoryId: line.Item?.ClassifiedTaxCategory?.ID?.trim() ?? '',
            writtenReason: line.Item?.ClassifiedTaxCategory?.TaxExemptionReason?.trim() ?? '',
        }))
        .filter(item => item.selectedTaxCategoryId && item.writtenReason);
}

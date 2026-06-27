import {getLines, type ClassifiedTaxCategory, type UBLDoc, type UBLLine} from "../peppol/ubl";

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

export const NOT_SUBJECT_TO_VAT_REASON_TEXT = 'Not subject to VAT';

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
        TaxExemptionReason: NOT_SUBJECT_TO_VAT_REASON_TEXT,
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

export function getDisplayedVatRatePercent(taxCategory: ClassifiedTaxCategory | undefined): number | undefined {
    if (!taxCategory) {
        return undefined;
    }

    if (taxCategory.ID?.trim().toUpperCase() === 'O') {
        return 0;
    }

    return taxCategory.Percent;
}

export function getReadonlyDisplayedVatRatePercent(taxCategory: ClassifiedTaxCategory | undefined): number {
    return getDisplayedVatRatePercent(taxCategory) ?? 0;
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

export function getSharedVatReasonText(
    doc: UBLDoc | undefined,
    reasonId: ZeroVatReasonId,
    sourceLine?: UBLLine,
): string | undefined {
    if (reasonId !== 'E') {
        return undefined;
    }

    return (getLines(doc) ?? [])
        .filter(line => line !== sourceLine)
        .map(line => line.Item?.ClassifiedTaxCategory)
        .find(taxCategory => taxCategory?.ID?.trim() === reasonId && !!taxCategory.TaxExemptionReason?.trim())
        ?.TaxExemptionReason?.trim();
}

export function applySharedVatReasonText(
    doc: UBLDoc | undefined,
    reasonId: ZeroVatReasonId,
    reasonText: string,
    sourceLine?: UBLLine,
) {
    const trimmedReasonText = reasonText?.trim();
    if (reasonId !== 'E' || !trimmedReasonText) {
        return;
    }

    for (const line of getLines(doc) ?? []) {
        if (line === sourceLine) {
            continue;
        }

        const taxCategory = line.Item?.ClassifiedTaxCategory;
        if (taxCategory?.ID?.trim() !== reasonId) {
            continue;
        }

        taxCategory.TaxExemptionReason = trimmedReasonText;
    }
}

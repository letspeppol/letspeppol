import {type ClassifiedTaxCategory, type TaxCategory, type UBLDoc, type UBLLine} from "../peppol/ubl";

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
const NOT_SUBJECT_TO_VAT_CATEGORY_ID = 'O';

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
        TaxScheme: { ID: 'VAT' }
    };
}

export function createNotSubjectToVatCategory(): ClassifiedTaxCategory {
    return {
        ID: NOT_SUBJECT_TO_VAT_CATEGORY_ID,
        Percent: undefined,
        TaxScheme: { ID: 'VAT' }
    };
}

export function createVatExemptCategory(): ClassifiedTaxCategory {
    return {
        ID: 'E',
        Percent: 0,
        TaxScheme: { ID: 'VAT' }
    };
}

export function getDisplayedVatRatePercent(taxCategory: ClassifiedTaxCategory | undefined): number | undefined {
    if (!taxCategory) {
        return undefined;
    }

    if (taxCategory.ID?.trim().toUpperCase() === NOT_SUBJECT_TO_VAT_CATEGORY_ID) {
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

export function supportsTaxExemptionReasonText(reasonId: ZeroVatReasonId | string | undefined): boolean {
    const normalizedReasonId = reasonId?.trim().toUpperCase();
    return normalizedReasonId === NOT_SUBJECT_TO_VAT_CATEGORY_ID
        || (normalizedReasonId !== 'Z' && ZERO_VAT_REASON_OPTIONS.some(item => item === normalizedReasonId));
}

function isRecordableVatReasonSelection(reasonId: ZeroVatReasonId | string | undefined): boolean {
    const normalizedReasonId = reasonId?.trim().toUpperCase();
    return normalizedReasonId === NOT_SUBJECT_TO_VAT_CATEGORY_ID
        || ZERO_VAT_REASON_OPTIONS.some(item => item === normalizedReasonId);
}

export function collectVatReasonSelections(doc: UBLDoc | undefined): VatReasonSelectionPayload[] {
    return (doc?.TaxTotal ?? [])
        .flatMap(taxTotal => taxTotal.TaxSubtotal ?? [])
        .map(subtotal => {
            const selectedTaxCategoryId = subtotal.TaxCategory?.ID?.trim() ?? '';
            return {
                selectedTaxCategoryId,
                writtenReason: subtotal.TaxCategory?.TaxExemptionReason?.trim() ?? '',
            };
        })
        .filter(item => isRecordableVatReasonSelection(item.selectedTaxCategoryId) && item.writtenReason);
}

export function getSharedVatReasonText(
    doc: UBLDoc | undefined,
    reasonId: ZeroVatReasonId | string | undefined,
    _sourceLine?: UBLLine,
): string | undefined {
    void _sourceLine;
    return findTaxCategory(doc, reasonId)
        ?.TaxExemptionReason?.trim() || undefined;
}

export function applySharedVatReasonText(
    doc: UBLDoc | undefined,
    reasonId: ZeroVatReasonId | string,
    reasonText: string,
    _sourceLine?: UBLLine,
) {
    void _sourceLine;
    const trimmedReasonText = reasonText?.trim();
    if (!supportsTaxExemptionReasonText(reasonId) || !trimmedReasonText) {
        return;
    }

    const taxCategory = findTaxCategory(doc, reasonId);
    if (taxCategory) {
        taxCategory.TaxExemptionReason = trimmedReasonText;
    }
}

function findTaxCategory(doc: UBLDoc | undefined, reasonId: ZeroVatReasonId | string | undefined): TaxCategory | undefined {
    const normalizedReasonId = reasonId?.trim().toUpperCase();
    if (!normalizedReasonId) {
        return undefined;
    }

    return (doc?.TaxTotal ?? [])
        .flatMap(taxTotal => taxTotal.TaxSubtotal ?? [])
        .map(subtotal => subtotal.TaxCategory)
        .find(taxCategory => taxCategory?.ID?.trim().toUpperCase() === normalizedReasonId);
}

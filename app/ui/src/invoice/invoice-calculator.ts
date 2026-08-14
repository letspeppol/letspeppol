import {singleton} from "aurelia";
import {ClassifiedTaxCategory, getLines, TaxCategory, TaxSubtotal, UBLDoc} from "../services/peppol/ubl";
import {getZeroVatReasonCode, NOT_SUBJECT_TO_VAT_REASON_TEXT, type ZeroVatReasonId} from "../services/app/vat-rules";

@singleton
export class InvoiceCalculator {

    public calculateTaxAndTotals(doc: UBLDoc) {
        const lines = getLines(doc);
        const previousTaxCategories = collectExistingTaxCategories(doc);

        let taxTotal = 0;
        let totalWithoutTax = 0;
        const taxSubtotals: TaxSubtotal[] = [];
        for (const line of lines) {
            const normalizedTaxCategory = normalizeTaxCategory(line.Item.ClassifiedTaxCategory);
            const previousTaxCategory = previousTaxCategories.get(taxBreakdownKey(normalizedTaxCategory) ?? '');
            let taxSubtotal = taxSubtotals.find(item => matchesTaxBreakdownKey(item.TaxCategory, normalizedTaxCategory));
            if (!taxSubtotal) {
                taxSubtotal = {
                    TaxableAmount: {
                        value: 0,
                        __currencyID: "EUR"
                    },
                    TaxAmount: {
                        value: 0,
                        __currencyID: "EUR"
                    },
                    TaxCategory: normalizedTaxCategory ? {...normalizedTaxCategory} : undefined
                }
                taxSubtotals.push(taxSubtotal);
            }
            taxSubtotal.TaxCategory = mergeTaxCategoryDetails(taxSubtotal.TaxCategory, normalizedTaxCategory);
            taxSubtotal.TaxCategory = preserveExistingTaxCategoryDetails(taxSubtotal.TaxCategory, previousTaxCategory);
            taxSubtotal.TaxableAmount.value += line.LineExtensionAmount.value;
            totalWithoutTax += line.LineExtensionAmount.value;
            const tax = roundTwoDecimals(line.LineExtensionAmount.value * ((normalizedTaxCategory.Percent ?? 0) / 100.0));
            taxSubtotal.TaxAmount.value += tax;
            taxTotal += tax;
        }
        taxTotal = roundTwoDecimals(taxTotal);
        totalWithoutTax = roundTwoDecimals(totalWithoutTax);
        const totalWithTax = roundTwoDecimals(totalWithoutTax + taxTotal);
        taxSubtotals.forEach(item => {
            item.TaxableAmount.value = roundTwoDecimals(item.TaxableAmount.value);
            item.TaxAmount.value = roundTwoDecimals(item.TaxAmount.value);
        });

        let chargeTotalAmount = 0;
        let allowanceTotalAmount = 0;

        if (doc.AllowanceCharge && doc.AllowanceCharge.length) {
            for (const allowanceCharge of doc.AllowanceCharge) {
                if (allowanceCharge.ChargeIndicator) {
                    chargeTotalAmount += allowanceCharge.Amount.value;
                } else {
                    allowanceTotalAmount += allowanceCharge.Amount.value;
                }
            }
        }

        if (chargeTotalAmount > 0) {

        }

        doc.TaxTotal = [{
            TaxAmount: {
                __currencyID: "EUR",
                value: taxTotal
            },
            TaxSubtotal: taxSubtotals
        }];

        doc.LegalMonetaryTotal = {
            LineExtensionAmount: {
                __currencyID: "EUR",
                value: totalWithoutTax
            },
            TaxExclusiveAmount: {
                __currencyID: "EUR",
                value: totalWithoutTax
            },
            TaxInclusiveAmount: {
                __currencyID: "EUR",
                value: totalWithTax
            },
            PayableAmount: {
                __currencyID: "EUR",
                value: totalWithTax
            }
        };
    }
}

export function roundTwoDecimals(value: number): number {
    return Math.round((value + Number.EPSILON) * 100) / 100;
}

function normalizeTaxCategory(category: ClassifiedTaxCategory | undefined): ClassifiedTaxCategory | undefined {
    if (!category) {
        return undefined;
    }
    const categoryId = category.ID.trim();

    if (categoryId === 'Z') {
        return {
            ...category,
            ID: categoryId,
            TaxExemptionReasonCode: undefined,
            TaxExemptionReason: undefined
        };
    }
    if (categoryId === 'O') {
        return {
            ...category,
            ID: categoryId,
            Percent: undefined,
            TaxExemptionReasonCode: category.TaxExemptionReasonCode?.trim() || undefined,
            TaxExemptionReason: NOT_SUBJECT_TO_VAT_REASON_TEXT
        };
    }
    return {
        ...category,
        ID: categoryId,
        TaxExemptionReasonCode: getZeroVatReasonCode(categoryId as ZeroVatReasonId) ?? (category.TaxExemptionReasonCode?.trim() || undefined),
        TaxExemptionReason: undefined
    };
}

function collectExistingTaxCategories(doc: UBLDoc): Map<string, TaxCategory> {
    const result = new Map<string, TaxCategory>();
    for (const taxTotal of doc.TaxTotal ?? []) {
        for (const subtotal of taxTotal.TaxSubtotal ?? []) {
            const key = taxBreakdownKey(subtotal.TaxCategory);
            if (key && subtotal.TaxCategory) {
                result.set(key, subtotal.TaxCategory);
            }
        }
    }
    return result;
}

function taxBreakdownKey(category: TaxCategory | ClassifiedTaxCategory | undefined): string | undefined {
    const categoryId = category?.ID?.trim();
    if (!categoryId) {
        return undefined;
    }

    return `${categoryId}|${category.Percent ?? ''}`;
}

function matchesTaxBreakdownKey(
    left: TaxCategory,
    right: ClassifiedTaxCategory | undefined,
): boolean {
    if (!left || !right) {
        return left === right;
    }

    return left.Percent === right.Percent
        && left.ID === right.ID;
}

function mergeTaxCategoryDetails(
    current: TaxCategory | undefined,
    incoming: ClassifiedTaxCategory | undefined,
): TaxCategory | undefined {
    if (!current) {
        return incoming ? {...incoming} : undefined;
    }
    if (!incoming) {
        return current;
    }

    return {
        ...current,
        TaxScheme: current.TaxScheme ?? incoming.TaxScheme,
        TaxExemptionReasonCode: current.TaxExemptionReasonCode ?? incoming.TaxExemptionReasonCode,
        TaxExemptionReason: current.TaxExemptionReason ?? incoming.TaxExemptionReason,
    };
}

function preserveExistingTaxCategoryDetails(
    current: TaxCategory | undefined,
    existing: TaxCategory | undefined,
): TaxCategory | undefined {
    if (!current || !existing) {
        return current;
    }

    return {
        ...current,
        TaxExemptionReasonCode: existing.TaxExemptionReasonCode?.trim() || current.TaxExemptionReasonCode,
        TaxExemptionReason: existing.TaxExemptionReason?.trim() || current.TaxExemptionReason,
    };
}

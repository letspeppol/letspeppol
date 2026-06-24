import {singleton} from "aurelia";
import {ClassifiedTaxCategory, getLines, TaxCategory, TaxSubtotal, UBLDoc} from "../services/peppol/ubl";

@singleton
export class InvoiceCalculator {

    public calculateTaxAndTotals(doc: UBLDoc) {
        const lines = getLines(doc);

        let taxTotal = 0;
        let totalWithoutTax = 0;
        const taxSubtotals: TaxSubtotal[] = [];
        for (const line of lines) {
            const normalizedTaxCategory = normalizeTaxCategory(line.Item.ClassifiedTaxCategory);
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
        })

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
            TaxExemptionReasonCode: undefined,
            TaxExemptionReason: undefined
        };
    }
    return {
        ...category,
        ID: categoryId,
        TaxExemptionReasonCode: category.TaxExemptionReasonCode?.trim() || undefined,
        TaxExemptionReason: category.TaxExemptionReason?.trim() || undefined
    };
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

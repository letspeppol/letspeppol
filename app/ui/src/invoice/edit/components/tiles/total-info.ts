import {resolve} from "@aurelia/kernel";
import {bindable} from "aurelia";
import {InvoiceContext} from "../../../invoice-context";
import {TaxTotal} from "../../../../services/peppol/ubl";

export interface VatBreakdownRow {
    percentage: number;
    amount: number;
}

export function getVatBreakdownRows(taxTotal: TaxTotal | undefined): VatBreakdownRow[] {
    const amountsByPercentage = new Map<number, number>();

    for (const subtotal of taxTotal?.TaxSubtotal ?? []) {
        const percentage = subtotal.TaxCategory?.Percent;
        if (percentage === undefined || !Number.isFinite(percentage)) {
            continue;
        }

        amountsByPercentage.set(
            percentage,
            (amountsByPercentage.get(percentage) ?? 0) + subtotal.TaxAmount.value,
        );
    }

    if (amountsByPercentage.size <= 1) {
        return [];
    }

    return [...amountsByPercentage].map(([percentage, amount]) => ({percentage, amount}));
}

export class TotalInfo {
    private invoiceContext = resolve(InvoiceContext);
    @bindable taxTotal: TaxTotal | undefined;
    vatBreakdownRows: VatBreakdownRow[] = [];

    bound(): void {
        this.taxTotalChanged(
            this.taxTotal ?? this.invoiceContext.selectedInvoice?.TaxTotal?.[0],
        );
    }

    taxTotalChanged(taxTotal: TaxTotal | undefined): void {
        this.vatBreakdownRows = getVatBreakdownRows(taxTotal);
    }

    hasTaxInclusiveAmount(): boolean {
        return this.invoiceContext.selectedInvoice?.LegalMonetaryTotal?.TaxInclusiveAmount !== undefined;
    }

    showPrepaidAmount(): boolean {
        return (this.invoiceContext.selectedInvoice?.LegalMonetaryTotal?.PrepaidAmount?.value ?? 0) > 0;
    }

    showPayableAmount(): boolean {
        const totals = this.invoiceContext.selectedInvoice?.LegalMonetaryTotal;
        const taxInclusiveAmount = totals?.TaxInclusiveAmount?.value;
        const payableAmount = totals?.PayableAmount?.value;

        return taxInclusiveAmount !== undefined
            && payableAmount !== undefined
            && payableAmount > 0
            && payableAmount !== taxInclusiveAmount;
    }

    vatTooltip(): string | undefined {
        const taxTotal = this.invoiceContext.selectedInvoice?.TaxTotal?.[0];
        if (taxTotal?.TaxAmount?.value !== 0) {
            return undefined;
        }

        const reasons = [...new Set(
            (taxTotal.TaxSubtotal ?? [])
                .map(subtotal => subtotal.TaxCategory?.TaxExemptionReason?.trim())
                .filter(Boolean)
        )];

        return reasons.length ? reasons.join('\n') : undefined;
    }
}

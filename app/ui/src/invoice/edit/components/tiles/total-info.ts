import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";

export class TotalInfo {
    private invoiceContext = resolve(InvoiceContext);

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

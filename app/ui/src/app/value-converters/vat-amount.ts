import { valueConverter } from "aurelia";
import { VatDisplayMode } from "../../services/app/vat-display-service";

interface AmountPair {
    amountInclVat?: number;
    amountExclVat?: number;
}

@valueConverter('vatAmount')
export class VatAmountConverter {
    // No cross-fallback — returning gross under an 'excl. VAT' label would silently lie.
    toView(item: AmountPair | undefined, mode: VatDisplayMode): number | undefined {
        if (!item) return undefined;
        return mode === 'excl' ? item.amountExclVat : item.amountInclVat;
    }
}

import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../invoice-context";
import {ClassifiedTaxCategory, getAmount, UBLLine} from "../../../services/peppol/ubl";
import {InvoiceCalculator, roundTwoDecimals} from "../../invoice-calculator";
import {bindable} from "aurelia";

export class InvoiceEditItems {
    private invoiceContext = resolve(InvoiceContext);
    private invoiceCalculator = resolve(InvoiceCalculator);

    @bindable readOnly;

    taxCategories: ClassifiedTaxCategory[] = [
        { ID: "S", Percent: 21, TaxScheme: { ID: 'VAT' } },
        { ID: "S", Percent: 12, TaxScheme: { ID: 'VAT' } },
        { ID: "S", Percent: 6, TaxScheme: { ID: 'VAT' } },
        { ID: "Z", Percent: 0, TaxScheme: { ID: 'VAT' } },
    ];

    taxCategoryMatcher = (a: ClassifiedTaxCategory, b: ClassifiedTaxCategory) => {
        return a?.Percent === b?.Percent;
    };

    recalculateLinePositions() {
        for (let i = 0; i < this.invoiceContext.lines.length; i++) {
            this.invoiceContext.lines[i].ID = (i + 1).toString();
        }
    }

    calcLineTotal(line: UBLLine) {
        const quantity = getAmount(line);
        line.LineExtensionAmount.value = roundTwoDecimals(line.Price.PriceAmount.value * quantity.value);
        this.invoiceCalculator.calculateTaxAndTotals(this.invoiceContext.selectedInvoice);
    }

    deleteLine(line: UBLLine) {
        this.invoiceContext.lines.splice(this.invoiceContext.lines.findIndex(item => item === line), 1);
        this.invoiceCalculator.calculateTaxAndTotals(this.invoiceContext.selectedInvoice);
    }
}

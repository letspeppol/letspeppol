import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../invoice-context";
import {ClassifiedTaxCategory, CreditNoteLine, getAmount, InvoiceLine, UBLLine} from "../../../services/peppol/ubl";
import {InvoiceCalculator, roundTwoDecimals} from "../../invoice-calculator";
import {DocumentType} from "../../../services/app/invoice-service";
import {bindable} from "aurelia";

export class InvoiceEditItems {
    private invoiceContext = resolve(InvoiceContext);
    private invoiceCalculator = resolve(InvoiceCalculator);

    @bindable readOnly;
    @bindable autoSave;

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
        this.checkLineAutoSave(line);
    }

    deleteLine(line: UBLLine) {
        this.invoiceContext.lines.splice(this.invoiceContext.lines.findIndex(item => item === line), 1);
        this.invoiceCalculator.calculateTaxAndTotals(this.invoiceContext.selectedInvoice);
        this.autoSave();
    }

    nameOnChange(e: UIEvent, line: UBLLine) {
        this.checkLineAutoSave(line);
    }

    checkLineAutoSave(line: InvoiceLine | CreditNoteLine) {
        let autosave = false;
        if (line?.Item?.Name && line?.Price?.PriceAmount?.value) {
            if (this.invoiceContext.selectedDocumentType === DocumentType.INVOICE && (line as InvoiceLine).InvoicedQuantity?.value
                || this.invoiceContext.selectedDocumentType === DocumentType.CREDIT_NOTE && (line as CreditNoteLine).CreditedQuantity?.value) {
                autosave = true;
            }
        }
        if (autosave) {
            this.autoSave();
        }
    }
}

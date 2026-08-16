import {resolve} from "@aurelia/kernel";
import {InvoiceCalculator} from "../../../invoice-calculator";
import {InvoiceContext} from "../../../invoice-context";
import {AllowanceCharge, getAmount, normalizeLinePrice, UBLBase, UBLBaseLine} from "../../../../services/peppol/ubl";
import {VAT_RATE_OPTIONS} from "../../../../services/app/vat-rules";

export type AllowanceChargeMode = 'percentage' | 'amount';
export type AllowanceChargeReason = 'discount' | 'cost';

export class InvoiceAllowanceChargeModal {
    private invoiceContext = resolve(InvoiceContext);
    private invoiceCalculator = resolve(InvoiceCalculator);
    open = false;
    mode: AllowanceChargeMode = 'percentage';
    reason: AllowanceChargeReason = 'discount';
    vatRateOptions = VAT_RATE_OPTIONS;
    vatRate = 21;
    allowanceCharge: AllowanceCharge[];
    parent: UBLBase | UBLBaseLine;
    amount: number;
    showModal(parent: UBLBase | UBLBaseLine) {
        this.parent = parent;
        if (parent.AllowanceCharge) {
            this.allowanceCharge = structuredClone(parent.AllowanceCharge);
        } else {
            this.allowanceCharge = [];
        }
        this.open = true;
    }

    addLine() {
        const amount = Number(this.amount);
        const vatRate = Number(this.vatRate);
        const taxCategory = this.isUBLBaseLine(this.parent) ? undefined : {
            ID: vatRate === 0 ? 'Z' : 'S',
            Percent: vatRate,
            TaxScheme: {ID: 'VAT'},
        };
        if (this.mode === 'percentage') {
            let baseAmount: number;
            if (this.isUBLBaseLine(this.parent)) {
                const quantity = getAmount(this.parent)?.value ?? 0;
                baseAmount = normalizeLinePrice(this.parent) * quantity;
            } else {
                baseAmount = this.parent.LegalMonetaryTotal.LineExtensionAmount.value;
            }
            this.allowanceCharge.push({
                ChargeIndicator: this.reason === 'cost',
                Amount: {
                    value: baseAmount * amount / 100,
                    __currencyID: "EUR"
                },
                MultiplierFactorNumeric: amount,
                ...(taxCategory ? {TaxCategory: taxCategory} : {}),
            });
        } else {
            this.allowanceCharge.push({
                ChargeIndicator: this.reason === 'cost',
                Amount: {
                    value: amount,
                    __currencyID: "EUR"
                },
                ...(taxCategory ? {TaxCategory: taxCategory} : {}),
            });
        }
    }

    vatRateChanged(value: number | string) {
        const vatRate = Number(value);
        if (!Number.isNaN(vatRate)) {
            this.vatRate = vatRate;
        }
    }

    isUBLBaseLine(parent: UBLBase | UBLBaseLine | undefined): parent is UBLBaseLine {
        return !!parent && "LineExtensionAmount" in parent;
    }

    deleteLine(line: AllowanceCharge) {
        const index = this.allowanceCharge.findIndex(item => item === line);
        if (index >= 0) {
            this.allowanceCharge.splice(index, 1);
        }
    }


    cancelAllowanceCharge() {
        this.open = false;
    }

    deleteAllowanceCharge() {
        delete this.parent.AllowanceCharge;
        this.allowanceCharge = [];
        if (this.isUBLBaseLine(this.parent)) {
            this.invoiceCalculator.recalculateLineExtensionAmount(this.parent);
        }
        if (this.invoiceContext.selectedInvoice) {
            this.invoiceCalculator.calculateTaxAndTotals(this.invoiceContext.selectedInvoice);
        }
        this.open = false;
    }

    confirmAllowanceCharge() {
        this.parent.AllowanceCharge = this.allowanceCharge;
        if (this.isUBLBaseLine(this.parent)) {
            this.invoiceCalculator.recalculateLineExtensionAmount(this.parent);
        }
        if (this.invoiceContext.selectedInvoice) {
            this.invoiceCalculator.calculateTaxAndTotals(this.invoiceContext.selectedInvoice);
        }
        this.open = false;
    }

}
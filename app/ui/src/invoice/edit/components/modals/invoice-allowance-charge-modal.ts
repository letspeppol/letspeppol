import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";
import {AllowanceCharge, UBLBase, UBLBaseLine} from "../../../../services/peppol/ubl";

export type AllowanceChargeMode = 'percentage' | 'amount';
export type AllowanceChargeReason = 'discount' | 'cost';

export class InvoiceAllowanceChargeModal {
    private invoiceContext = resolve(InvoiceContext);
    open = false;
    mode: AllowanceChargeMode = 'percentage';
    reason: AllowanceChargeReason = 'discount';
    allowanceCharge: AllowanceCharge[];
    parent: UBLBase | UBLBaseLine;
    amount: number;
    showModal(parent: UBLBase | UBLBaseLine) {
        if (parent.AllowanceCharge) {
            this.allowanceCharge = structuredClone(parent.AllowanceCharge);
        } else {
            this.allowanceCharge = [];
        }
        this.open = true;
    }

    addLine() {
        if (this.mode === 'percentage') {
            let baseAmount: number;
            if (this.isUBLBaseLine(this.parent)) {
                baseAmount = this.parent.Price?.PriceAmount.value;
            } else {
                baseAmount = this.parent.LegalMonetaryTotal.LineExtensionAmount.value;
            }
            this.allowanceCharge.push({
                ChargeIndicator: this.reason === 'cost',
                Amount: {
                    value: baseAmount * this.amount / 100,
                    __currencyID: "EUR"
                }
            });
        } else {
            this.allowanceCharge.push({
                ChargeIndicator: this.reason === 'cost',
                Amount: {
                    value: this.amount,
                    __currencyID: "EUR"
                }
            });
        }
    }

    isUBLBaseLine(parent: UBLBase | UBLBaseLine): parent is UBLBaseLine {
        return "LineExtensionAmount" in parent;
    }

    deleteLine(line: AllowanceCharge) {
        this.allowanceCharge.splice(this.invoiceContext.lines.findIndex(item => item === line), 1);
    }

    cancelAllowanceCharge() {
        this.open = false;
    }

    deleteAllowanceCharge() {
        delete this.parent.AllowanceCharge
        this.open = false;
    }

    confirmAllowanceCharge() {
        this.parent.AllowanceCharge = this.allowanceCharge;
        this.open = false;
    }

}
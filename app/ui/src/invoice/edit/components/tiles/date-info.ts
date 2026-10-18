import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";
import {bindable} from "aurelia";
import {isCreditNoteType} from "../../../../services/app/invoice-service";
import {requiresDeliveryDetails} from "../../../../services/app/vat-rules";
import {hasAllowedDueDate} from "../../invoice-validation";

export class DateInfo {
    private invoiceContext = resolve(InvoiceContext);

    @bindable readOnly: boolean;
    @bindable showDateModal;

    get isCreditNote(): boolean {
        return isCreditNoteType(this.invoiceContext.selectedDocumentType);
    }

    get isDateInfoComplete(): boolean {
        const invoice = this.invoiceContext.selectedInvoice;
        if (!this.readOnly && invoice && !hasAllowedDueDate(invoice)) {
            return false;
        }
        const requiresExtraDetails = this.invoiceContext.lines?.some(line => requiresDeliveryDetails(line.Item?.ClassifiedTaxCategory?.ID)) ?? false;
        if (!requiresExtraDetails) {
            return true;
        }
        const delivery = this.invoiceContext.selectedInvoice?.Delivery;
        return !!delivery?.ActualDeliveryDate && !!delivery?.DeliveryLocation?.Address?.Country?.IdentificationCode;
    }
}

import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";
import {bindable} from "aurelia";
import {DocumentType} from "../../../../services/app/invoice-service";
import {requiresDeliveryDetails} from "../../../../services/app/vat-rules";

export class DateInfo {
    private invoiceContext = resolve(InvoiceContext);

    @bindable readOnly: boolean;
    @bindable showDateModal;

    get isCreditNote(): boolean {
        return this.invoiceContext.selectedDocumentType === DocumentType.CREDIT_NOTE;
    }

    get isDateInfoComplete(): boolean {
        const requiresExtraDetails = this.invoiceContext.lines?.some(line => requiresDeliveryDetails(line.Item?.ClassifiedTaxCategory?.ID)) ?? false;
        if (!requiresExtraDetails) {
            return true;
        }
        const delivery = this.invoiceContext.selectedInvoice?.Delivery;
        return !!delivery?.ActualDeliveryDate && !!delivery?.DeliveryLocation?.Address?.Country?.IdentificationCode;
    }
}

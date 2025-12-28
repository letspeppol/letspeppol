import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";
import {bindable} from "aurelia";

export class DateInfo {
    private invoiceContext = resolve(InvoiceContext);

    @bindable readOnly: boolean;
    @bindable showDateModal;
}

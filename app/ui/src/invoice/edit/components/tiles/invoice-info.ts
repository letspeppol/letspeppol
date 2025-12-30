import {bindable} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";

export class InvoiceInfo {
    private invoiceContext = resolve(InvoiceContext);

    @bindable readOnly: boolean;
    @bindable showInvoiceModal;
}

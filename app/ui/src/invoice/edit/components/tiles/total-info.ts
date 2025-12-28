import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";

export class TotalInfo {
    private invoiceContext = resolve(InvoiceContext);
}

import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "./invoice-context";

export class Invoices {
    private invoiceContext = resolve(InvoiceContext);

    detaching() {
        this.invoiceContext.clearSelectedInvoice();
    }

}

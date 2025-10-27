import {resolve} from "@aurelia/kernel";
import {ProxyService} from "../services/proxy-service";
import {InvoiceContext} from "./invoice-context";

export class Invoices {
    private invoiceContext = resolve(InvoiceContext);
}
import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {ICurrentRoute, Params} from "@aurelia/router";
import {InvoiceContext} from "./invoice-context";
import {InvoiceService} from "../services/app/invoice-service";
import {AlertType} from "../components/alert/alert";

export class Invoices {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly currentRoute = resolve(ICurrentRoute);
    private invoiceContext = resolve(InvoiceContext);
    private invoiceService = resolve(InvoiceService);

    attached() {
        // We fiddle with history state when selecting an invoice
        window.addEventListener('popstate', () => {
            if (window.location.pathname === '/invoices') {
                this.invoiceContext.clearSelectedInvoice();
            }
        });
    }

    loading(params: Params) {
        if (params.id) {
            this.invoiceService.getDocument(params.id)
                .then((doc) => this.invoiceContext.selectInvoice(doc))
                .catch(() => this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to get invoice"}));
        }
    }

}

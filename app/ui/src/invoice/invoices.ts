import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {Params} from "@aurelia/router";
import {InvoiceContext} from "./invoice-context";
import {InvoiceService} from "../services/app/invoice-service";
import {AlertType} from "../components/alert/alert";
import {I18N} from "@aurelia/i18n";

export class Invoices {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private invoiceContext = resolve(InvoiceContext);
    private invoiceService = resolve(InvoiceService);
    private readonly i18n = resolve(I18N);

    detaching() {
        this.invoiceContext.clearSelectedInvoice();
    }

    loading(params: Params) {
        const requestedId = params.id;
        // Track the invoice id the router is navigating to, so a late getDocument()
        // response can't re-select an invoice after the user already navigated away.
        this.invoiceContext.selectedRouteId = requestedId;
        if (requestedId) {
            this.invoiceService.getDocument(requestedId).then((doc) => {
                if (this.invoiceContext.selectedRouteId === requestedId) {
                    this.invoiceContext.selectInvoice(doc);
                }
            }).catch(() => {
                if (this.invoiceContext.selectedRouteId === requestedId) {
                    this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.invoice.load-failed')});
                }
            });
        } else {
            this.invoiceContext.clearSelectedInvoice();
            this.ea.publish('invoicesReset');
        }
    }

}

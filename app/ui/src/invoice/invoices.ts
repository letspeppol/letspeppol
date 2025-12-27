import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "./invoice-context";
import {Params} from "@aurelia/router";
import {InvoiceService} from "../services/app/invoice-service";
import {AlertType} from "../components/alert/alert";
import {IEventAggregator} from "aurelia";

export class Invoices {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private invoiceContext = resolve(InvoiceContext);
    private invoiceService = resolve(InvoiceService);

    loading(params: Params) {
        if (params.id) {
            this.invoiceService.getDocument(params.id)
                .then((doc) => this.invoiceContext.selectInvoice(doc))
                .catch(() => this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to get invoice"}));
        }
    }

}

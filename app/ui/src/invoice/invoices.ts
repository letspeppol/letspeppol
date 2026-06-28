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
        this.invoiceContext.selectedInvoice = undefined;
        this.invoiceContext.selectedDocument = undefined;
    }

    loading(params: Params) {
        if (params.id) {
            this.invoiceService.getDocument(params.id).then((doc) => {
                this.invoiceContext.selectInvoice(doc);
            }).catch(() => this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.invoice.load-failed')}));
        } else {
            this.invoiceContext.selectedInvoice = undefined;
            this.invoiceContext.selectedDocument = undefined;
            this.ea.publish('invoicesReset');
        }
    }

}

import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";
import {bindable, computed} from "aurelia";
import {InvoiceComposer} from "../../../invoice-composer";

export class PaymentInfo {
    private invoiceContext = resolve(InvoiceContext);
    private invoiceComposer = resolve(InvoiceComposer);

    @bindable readOnly: boolean;
    @bindable showPaymentModal;

    @computed({
        deps: [
            'invoiceContext.selectedInvoice.PaymentMeans.PaymentMeansCode.value',
            'invoiceContext.selectedInvoice.PaymentMeans.PayeeFinancialAccount.ID'
        ] })
    get isPaymentInfoComplete(): boolean {
        const inv = this.invoiceContext.selectedInvoice;
        return !inv?.PaymentMeans
            || (inv?.PaymentMeans.PaymentMeansCode.value != 30
                || (inv?.PaymentMeans.PaymentMeansCode.value === 30 && !!inv?.PaymentMeans.PayeeFinancialAccount.ID));
    }
}

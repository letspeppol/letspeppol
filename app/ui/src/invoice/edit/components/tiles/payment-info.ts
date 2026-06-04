import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";
import {bindable, computed, IEventAggregator} from "aurelia";
import {InvoiceComposer} from "../../../invoice-composer";
import {InvoiceService, DocumentDirection, DocumentType} from "../../../../services/app/invoice-service";
import {AlertType} from "../../../../components/alert/alert";
import moment from "moment";
import {I18N} from "@aurelia/i18n";

export class PaymentInfo {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private invoiceService = resolve(InvoiceService);
    private invoiceContext = resolve(InvoiceContext);
    private invoiceComposer = resolve(InvoiceComposer);
    private readonly i18n = resolve(I18N);

    @bindable readOnly: boolean;
    @bindable showPaymentModal;
    paymentQrModalOpen = false;

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

    get shouldShowPaymentQrAction(): boolean {
        return !this.invoiceContext.selectedDocument?.paidOn
            && this.isSuccessfullyProcessed
            && this.invoiceContext.selectedDocument?.direction === DocumentDirection.INCOMING
            && this.invoiceContext.selectedDocument?.type !== DocumentType.CREDIT_NOTE
            && !!this.invoiceContext.selectedInvoice?.PaymentMeans?.PayeeFinancialAccount?.ID;
    }

    get isSuccessfullyProcessed(): boolean {
        const document = this.invoiceContext.selectedDocument;
        return !!document?.processedOn && !document?.processedStatus;
    }

    showPaymentQrModal() {
        this.paymentQrModalOpen = true;
    }

    closePaymentQrModal() {
        this.paymentQrModalOpen = false;
    }

    async markPaid(event?: Event) {
        event?.stopPropagation();
        const document = this.invoiceContext.selectedDocument;

        if (!document?.id) {
            return;
        }

        try {
            const datePaid = moment().toISOString();
            await this.invoiceService.togglePaidDocument(document.id);
            if (document.paidOn) {
                document.paidOn = undefined;
                this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr(`alert.invoice.marked-unpaid.${document.type}`)});
            } else {
                document.paidOn = datePaid;
                this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr(`alert.invoice.marked-paid.${document.type}`)});
            }
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('alert.invoice.paid-status-failed')});
        }
    }

    async markPaidAndClose(event?: Event) {
        await this.markPaid(event);
        this.closePaymentQrModal();
    }

}

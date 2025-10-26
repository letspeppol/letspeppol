import {bindable, observable} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {I18N} from "@aurelia/i18n";
import {Account} from "../../../account/account";
import moment from "moment";

export interface Translation {
    key: string,
    translation: string
}

export class InvoiceDateModal {
    private i18n = resolve(I18N);
    @bindable invoiceContext;
    @observable issueDate;
    @observable selectedPaymentTerm;
    dueDate;
    open = false;
    possiblePaymentTerms: Translation[];

    showModal() {
        this.loadPossiblePaymentTerms();
        this.issueDate = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.IssueDate));
        this.selectedPaymentTerm = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.PaymentTerms));
        this.dueDate = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.IssueDate));
        this.open = true;
    }

    issueDateChanged() {
        this.recalculateDueDate();
    }

    selectedPaymentTermChanged() {
        this.recalculateDueDate();
    }

    private recalculateDueDate() {
        const date = moment(this.issueDate);
        switch (this.selectedPaymentTerm) {
            case '15_DAYS':
                date.add(15, 'day');
                break;
            case '30_DAYS':
                date.add(30, 'day');
                break;
            case '60_DAYS':
                date.add(60, 'day');
                break;
            case 'END_OF_NEXT_MONTH':
                date.add(1, 'month').endOf('month');
                break;
        }
        this.dueDate = date.format('YYYY-MM-DD');
        console.log(this.dueDate);
    }

    private closeModal() {
        this.open = false;
    }

    private saveDate() {
        this.open = false;
        this.invoiceContext.selectedInvoice.IssueDate = this.issueDate;
        this.invoiceContext.selectedInvoice.DueDate = this.dueDate;
        this.invoiceContext.selectedInvoice.PaymentTerms = this.selectedPaymentTerm;
    }

    private loadPossiblePaymentTerms() {
        this.possiblePaymentTerms = [];
        for (const paymentTerm of Account.PAYMENT_TERMS) {
            const translation = this.translatePaymentTerm(paymentTerm);
            if (translation === this.invoiceContext.selectedInvoice.PaymentTerms) {
                this.selectedPaymentTerm = paymentTerm;
            }
            this.possiblePaymentTerms.push({key: paymentTerm, translation: translation});
        }
    }

    private translatePaymentTerm(paymentTerm: string) {
        return this.i18n.tr(`paymentTerms.${paymentTerm}`)
    }
}
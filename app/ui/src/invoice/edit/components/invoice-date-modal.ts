import {bindable, observable} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {Account} from "../../../account/account";
import {InvoiceComposer} from "../../invoice-composer";

export interface Translation {
    key: string,
    translation: string
}

export class InvoiceDateModal {
    private invoiceComposer = resolve(InvoiceComposer);
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
        this.dueDate = this.invoiceComposer.getDueDate(this.selectedPaymentTerm, this.issueDate);
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
            const translation = this.invoiceComposer.translatePaymentTerm(paymentTerm);
            if (translation === this.invoiceContext.selectedInvoice.PaymentTerms) {
                this.selectedPaymentTerm = paymentTerm;
            }
            this.possiblePaymentTerms.push({key: paymentTerm, translation: translation});
        }
    }
}
import {bindable, observable} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {Account} from "../../../account/account";
import {InvoiceComposer} from "../../invoice-composer";
import {DocumentType} from "../../invoice-context";

export interface Translation {
    key: string,
    translation: string
}

export class InvoiceDateModal {
    private invoiceComposer = resolve(InvoiceComposer);
    @bindable invoiceContext;
    @bindable documentType: DocumentType;
    @observable issueDate;
    @observable selectedPaymentTerm;
    dueDate;
    open = false;
    possiblePaymentTerms: Translation[];

    showModal() {
        this.loadPossiblePaymentTerms();
        this.issueDate = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.IssueDate));
        // if (this.invoiceContext.selectedInvoice.PaymentTerms) {
        //     this.selectedPaymentTerm = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.PaymentTerms));
        // } else {
        //     this.selectedPaymentTerm = undefined;
        // }
        if (this.invoiceContext.selectedInvoice.dueDate) {
            this.dueDate = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.dueDate));
        } else {
            this.dueDate = undefined;
        }
        this.open = true;
    }

    issueDateChanged() {
        this.recalculateDueDate();
    }

    selectedPaymentTermChanged() {
        this.recalculateDueDate();
    }

    private recalculateDueDate() {
        if (this.documentType === DocumentType.CreditNote) {
            return;
        }
        this.dueDate = this.invoiceComposer.getDueDate(this.selectedPaymentTerm, this.issueDate);
    }

    private closeModal() {
        this.open = false;
    }

    private saveDate() {
        this.open = false;
        this.invoiceContext.selectedInvoice.IssueDate = this.issueDate;
        this.invoiceContext.selectedInvoice.DueDate = this.dueDate;
        if (this.selectedPaymentTerm) {
            this.invoiceContext.selectedInvoice.PaymentTerms = {
                Note: this.invoiceComposer.translatePaymentTerm(this.selectedPaymentTerm)
            };
        }
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
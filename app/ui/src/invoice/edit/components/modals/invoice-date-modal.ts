import {bindable, observable} from "aurelia";
import {onModalEnter} from "../../../../components/util/modal-keyboard";
import {resolve} from "@aurelia/kernel";
import {Account} from "../../../../account/account";
import {InvoiceComposer} from "../../../invoice-composer";
import {DocumentType} from "../../../../services/app/invoice-service";

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
        const invoice = this.invoiceContext.selectedInvoice;
        this.issueDate = JSON.parse(JSON.stringify(invoice.IssueDate));
        this.dueDate = invoice.DueDate ? JSON.parse(JSON.stringify(invoice.DueDate)) : undefined;
        this.loadPossiblePaymentTerms();
        if (!this.dueDate) {
            this.recalculateDueDate();
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
        if (this.documentType === DocumentType.CREDIT_NOTE) {
            return;
        }
        if (!this.issueDate || !this.selectedPaymentTerm) {
            return;
        }
        this.dueDate = this.invoiceComposer.getDueDate(this.selectedPaymentTerm, this.issueDate);
    }

    private closeModal() {
        this.open = false;
    }

    private saveDate() {
        if (!this.issueDate) {
            return;
        }
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
        let selectedPaymentTerm: string = undefined;
        const paymentTermNote = this.invoiceContext.selectedInvoice.PaymentTerms?.Note;
        this.possiblePaymentTerms = [];
        for (const paymentTerm of Account.PAYMENT_TERMS) {
            const translation = this.invoiceComposer.translatePaymentTerm(paymentTerm);
            if (paymentTermNote && translation === paymentTermNote) {
                selectedPaymentTerm = paymentTerm;
            }
            this.possiblePaymentTerms.push({key: paymentTerm, translation: translation});
        }
        if (!selectedPaymentTerm && this.documentType === DocumentType.INVOICE && this.issueDate && this.dueDate) {
            selectedPaymentTerm = Account.PAYMENT_TERMS.find(paymentTerm =>
                this.invoiceComposer.getDueDate(paymentTerm, this.issueDate) === this.dueDate
            );
        }
        this.selectedPaymentTerm = selectedPaymentTerm;
    }

    onKeyDown(event: KeyboardEvent) {
        onModalEnter(event, () => this.saveDate());
    }
}

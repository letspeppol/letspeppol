import {bindable, observable} from "aurelia";
import {onModalEnter} from "../../../../components/util/modal-keyboard";
import {resolve} from "@aurelia/kernel";
import {Account} from "../../../../account/account";
import {InvoiceComposer} from "../../../invoice-composer";
import {DocumentType} from "../../../../services/app/invoice-service";
import {countryListAlpha2} from "../../../../app/countries";
import {requiresDeliveryDetails} from "../../../../services/app/vat-rules";

export interface Translation {
    key: string,
    translation: string
}

export class InvoiceDateModal {
    private invoiceComposer = resolve(InvoiceComposer);
    countryList = countryListAlpha2;
    @bindable invoiceContext;
    @bindable documentType: DocumentType;
    @observable issueDate;
    @observable selectedPaymentTerm;
    dueDate;
    actualDeliveryDate;
    deliveryCountryCode;
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
        this.actualDeliveryDate = this.invoiceContext.selectedInvoice.Delivery?.ActualDeliveryDate;
        this.deliveryCountryCode = this.invoiceContext.selectedInvoice.Delivery?.DeliveryLocation?.Address?.Country?.IdentificationCode;
        this.open = true;
    }

    get requiresDeliveryDetails(): boolean {
        return this.invoiceContext.lines?.some(line => requiresDeliveryDetails(line.Item?.ClassifiedTaxCategory?.ID)) ?? false;
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
        if (this.actualDeliveryDate || this.deliveryCountryCode) {
            if (!this.invoiceContext.selectedInvoice.Delivery) {
                this.invoiceContext.selectedInvoice.Delivery = {};
            }
            this.invoiceContext.selectedInvoice.Delivery.ActualDeliveryDate = this.actualDeliveryDate;
            if (!this.invoiceContext.selectedInvoice.Delivery.DeliveryLocation) {
                this.invoiceContext.selectedInvoice.Delivery.DeliveryLocation = {};
            }
            if (!this.invoiceContext.selectedInvoice.Delivery.DeliveryLocation.Address) {
                this.invoiceContext.selectedInvoice.Delivery.DeliveryLocation.Address = {};
            }
            this.invoiceContext.selectedInvoice.Delivery.DeliveryLocation.Address.Country = this.deliveryCountryCode
                ? {IdentificationCode: this.deliveryCountryCode}
                : undefined;
        } else {
            this.invoiceContext.selectedInvoice.Delivery = undefined;
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

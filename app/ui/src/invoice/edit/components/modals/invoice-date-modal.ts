import {bindable, observable} from "aurelia";
import {onModalEnter} from "../../../../components/util/modal-keyboard";
import {resolve} from "@aurelia/kernel";
import {InvoiceComposer} from "../../../invoice-composer";
import {DocumentType} from "../../../../services/app/invoice-service";
import {countryListAlpha2} from "../../../../app/countries";
import {requiresDeliveryDetails} from "../../../../services/app/vat-rules";

export class InvoiceDateModal {
    private invoiceComposer = resolve(InvoiceComposer);
    countryList = countryListAlpha2;
    @bindable invoiceContext;
    @bindable documentType: DocumentType;
    @observable issueDate;
    @observable selectedPaymentTerm;
    @observable dueDate;
    actualDeliveryDate;
    deliveryCountryCode;
    open = false;

    private syncing = false;
    private initialPaymentTerm;

    showModal() {
        const invoice = this.invoiceContext.selectedInvoice;
        this.syncing = true;
        this.issueDate = JSON.parse(JSON.stringify(invoice.IssueDate));
        this.dueDate = invoice.DueDate ? JSON.parse(JSON.stringify(invoice.DueDate)) : undefined;
        this.selectedPaymentTerm = this.invoiceComposer.derivePaymentTermCode(
            this.issueDate,
            this.dueDate,
            invoice.PaymentTerms?.Note,
        );
        this.initialPaymentTerm = this.selectedPaymentTerm;
        this.syncing = false;
        if (!this.dueDate) {
            this.recalculateDueDate();
        }
        this.actualDeliveryDate = invoice.Delivery?.ActualDeliveryDate;
        this.deliveryCountryCode = invoice.Delivery?.DeliveryLocation?.Address?.Country?.IdentificationCode;
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

    dueDateChanged() {
        if (this.syncing || this.documentType === DocumentType.CREDIT_NOTE || !this.issueDate || !this.dueDate) {
            return;
        }
        const derived = this.invoiceComposer.derivePaymentTermCode(this.issueDate, this.dueDate);
        if (!derived) {
            return;
        }
        this.syncing = true;
        this.selectedPaymentTerm = derived;
        this.syncing = false;
    }

    private recalculateDueDate() {
        if (this.syncing || this.documentType === DocumentType.CREDIT_NOTE) {
            return;
        }
        if (!this.issueDate || !this.selectedPaymentTerm) {
            return;
        }
        this.syncing = true;
        this.dueDate = this.invoiceComposer.getDueDate(this.selectedPaymentTerm, this.issueDate);
        this.syncing = false;
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
        } else if (this.initialPaymentTerm) {
            this.invoiceContext.selectedInvoice.PaymentTerms = undefined;
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

    onKeyDown(event: KeyboardEvent) {
        onModalEnter(event, () => this.saveDate());
    }
}

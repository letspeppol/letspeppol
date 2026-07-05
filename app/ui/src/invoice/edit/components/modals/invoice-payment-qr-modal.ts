import {bindable} from "aurelia";
import {DocumentType} from "../../../../services/app/invoice-service";
import {normalizeBelgianStructuredCommunication} from "../../../belgian-structured-communication";

export class InvoicePaymentQrModal {
    @bindable open = false;
    @bindable invoiceContext;
    @bindable close: () => void;
    @bindable markPaid: () => Promise<void> | void;

    get paymentAccount() {
        return this.invoiceContext?.selectedInvoice?.PaymentMeans?.PayeeFinancialAccount;
    }

    get name(): string {
        return this.paymentAccount?.Name
            || this.invoiceContext?.selectedInvoice?.AccountingSupplierParty?.Party?.PartyName?.Name
            || '';
    }

    get iban(): string {
        return this.paymentAccount?.ID || '';
    }

    get bic(): string {
        return this.paymentAccount?.FinancialInstitutionBranch?.ID || '';
    }

    get amount(): number {
        return this.invoiceContext?.selectedInvoice?.LegalMonetaryTotal?.PayableAmount?.value
            || this.invoiceContext?.selectedDocument?.amount
            || 0;
    }

    get amountLabel(): string {
        return new Intl.NumberFormat('nl-BE', {
            style: 'currency',
            currency: this.invoiceContext?.selectedInvoice?.DocumentCurrencyCode || this.invoiceContext?.selectedDocument?.currency || 'EUR',
            currencyDisplay: 'symbol',
        }).format(this.amount);
    }

    get reference(): string {
        return this.invoiceContext?.selectedInvoice?.PaymentMeans?.PaymentID
            || this.invoiceContext?.selectedInvoice?.ID
            || this.invoiceContext?.selectedDocument?.invoiceReference
            || '';
    }

    get creditorReference(): string {
        return normalizeBelgianStructuredCommunication(this.paymentId);
    }

    get remittanceInformation(): string {
        return this.creditorReference ? '' : this.reference;
    }

    get currentDocumentType(): DocumentType {
        return this.invoiceContext?.selectedDocument?.type || DocumentType.INVOICE;
    }

    private get paymentId(): string {
        return this.invoiceContext?.selectedInvoice?.PaymentMeans?.PaymentID || '';
    }

    closeModal() {
        this.close?.();
    }

    async markPaidAndClose() {
        await this.markPaid?.();
    }
}

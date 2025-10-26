import {resolve} from "@aurelia/kernel";
import {ProxyService} from "../../services/proxy/proxy-service";
import {DocumentType, InvoiceContext} from "../invoice-context";
import {bindable, IEventAggregator, observable} from "aurelia";
import {
    ClassifiedTaxCategory,
    CreditNote,
    getAmount,
    Invoice,
    PaymentMeansCode,
    UBLLine
} from "../../services/peppol/ubl";
import {AlertType} from "../../components/alert/alert";
import {buildCreditNote, buildInvoice, parseInvoice} from "../../services/peppol/ubl-parser";
import {InvoicePaymentModal} from "./components/invoice-payment-modal";
import {InvoiceCustomerModal} from "./components/invoice-customer-modal";
import {InvoiceCalculator, roundTwoDecimals} from "../invoice-calculator";
import {InvoiceComposer} from "../invoice-composer";
import {downloadInvoicePdf} from "../pdf/invoice-pdf";
import {InvoiceDraftDto, InvoiceService} from "../../services/app/invoice-service";
import {ValidationResultModal} from "./components/validation-result-modal";
import {InvoiceModal} from "./components/invoice-modal";

export class InvoiceEdit {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private proxyService = resolve(ProxyService);
    private invoiceService = resolve(InvoiceService);
    private invoiceContext = resolve(InvoiceContext);
    private invoiceCalculator = resolve(InvoiceCalculator);
    private invoiceComposer = resolve(InvoiceComposer);
    private documentTypes = Object.values(DocumentType) as string[];
    selectedPaymentMeansCode: number | undefined = 30;
    @observable selectedDocumentType = DocumentType.Invoice;
    @observable customerCompanyNumber: undefined | string;
    @bindable invoiceModal: InvoiceModal;
    @bindable invoiceDateModal: InvoicePaymentModal;
    @bindable invoiceCustomerModal: InvoiceCustomerModal;
    @bindable invoicePaymentModal: InvoicePaymentModal;
    @bindable validationResultModal: ValidationResultModal;

    taxCategories: ClassifiedTaxCategory[] = [
        { ID: "S", Percent: 21, TaxScheme: { ID: 'VAT' } },
        { ID: "S", Percent: 12, TaxScheme: { ID: 'VAT' } },
        { ID: "S", Percent: 6, TaxScheme: { ID: 'VAT' } },
        { ID: "Z", Percent: 0, TaxScheme: { ID: 'VAT' } },
    ];

    paymentMeanCodeMatcher = (a: PaymentMeansCode, b: PaymentMeansCode) => {
        return a?.value === b?.value;
    };

    calcLineTotal(line: UBLLine) {
        const quantity = getAmount(line);
        line.LineExtensionAmount.value = roundTwoDecimals(line.Price.PriceAmount.value * quantity.value);
        this.invoiceCalculator.calculateTaxAndTotals(this.invoiceContext.selectedInvoice);
    }

    addLine() {
        let line: UBLLine;
        if (this.selectedDocumentType === DocumentType.Invoice) {
            line = this.invoiceComposer.getInvoiceLine("1");
        } else {
            line = this.invoiceComposer.getCreditNoteLine("1");
        }
        this.invoiceContext.lines.push(line);
    }

    deleteLine(line: UBLLine) {
        this.invoiceContext.lines.splice(this.invoiceContext.lines.findIndex(item => item === line), 1);
        this.invoiceCalculator.calculateTaxAndTotals(this.invoiceContext.selectedInvoice);
    }

    async sendInvoice() {
        try {
            this.ea.publish('showOverlay', "Sending invoice");
            console.log(JSON.stringify(this.invoiceContext.selectedInvoice));
            let xml = this.buildXml();
            await this.proxyService.sendDocument(xml);
            console.log(xml);
            console.log(parseInvoice(xml));
        } catch(e) {
            console.error(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to update account"});
        } finally {
            this.ea.publish('hideOverlay');
        }
    }

    downloadPdf() {
        if (!this.invoiceContext.selectedInvoice) return;
        downloadInvoicePdf(this.invoiceContext.selectedInvoice);
    }

    buildXml(): string {
        if  (this.selectedDocumentType === DocumentType.Invoice)  {
            return buildInvoice(this.invoiceContext.selectedInvoice as Invoice)
        } else {
            return buildCreditNote(this.invoiceContext.selectedInvoice as CreditNote);
        }
    }

    async saveAsDraft() {
        try {
            const draft = this.convertInvoiceToDraft();
            if (this.invoiceContext.selectedDraft) {
                await this.invoiceService.updateInvoiceDraft(draft.id, draft);
            } else {
                const invoiceDraftDto = await this.invoiceService.createInvoiceDraft(draft);
                this.invoiceContext.drafts.unshift(invoiceDraftDto);
            }
            this.invoiceContext.selectedInvoice = undefined;
            this.invoiceContext.selectedDraft = undefined;
        } catch(e) {
            console.error(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to save invoice as draft"});
        }
    }

    convertInvoiceToDraft() {
        const xml = this.buildXml();
        return {
            id: this.invoiceContext.selectedDraft?.id,
            type: this.selectedDocumentType,
            number: this.invoiceContext.selectedInvoice.ID,
            customer: this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party?.PartyName.Name,
            date: this.invoiceContext.selectedInvoice.IssueDate,
            xml: xml
        } as InvoiceDraftDto;
    }

    async validate() {
        const form = document.getElementById('invoiceForm') as HTMLFormElement;
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }
        const xml = this.buildXml();
        const response = await this.invoiceService.validate(xml);
        this.validationResultModal.showModal(response);
        console.log(response);
    }

    customerCompanyNumberChanged(newValue: string) {
        this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party.EndpointID.value = newValue;
        this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party.PartyIdentification[0].ID.value = newValue;
        this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party.PartyTaxScheme.CompanyID.value = newValue;
        console.log(newValue);
    }

    selectedDocumentTypeChanged(newValue) {
        if (newValue === DocumentType.Invoice) {
            this.invoiceContext.selectedInvoice = this.invoiceComposer.creditNoteToInvoice(this.invoiceContext.selectedInvoice as unknown as CreditNote);
        } else {
            this.invoiceContext.selectedInvoice = this.invoiceComposer.invoiceToCreditNote(this.invoiceContext.selectedInvoice as Invoice);
        }
    }

    // Modals

    showInvoiceModal() {
        this.invoiceModal.showModal();
    }

    showDateModal() {
        this.invoiceDateModal.showModal();
    }

    showCustomerModal() {
        this.invoiceCustomerModal.showModal();
    }

    showPaymentModal() {
        this.invoicePaymentModal.showModal();
    }

}

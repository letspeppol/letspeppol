import {resolve} from "@aurelia/kernel";
import {ProxyService} from "../../services/proxy/proxy-service";
import {DocumentType, InvoiceContext} from "../invoice-context";
import {bindable, computed, IDisposable, IEventAggregator, observable} from "aurelia";
import {
    Attachment,
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
// import {downloadInvoicePdf} from "../pdf/invoice-pdf";
import {InvoiceDraftDto, InvoiceService} from "../../services/app/invoice-service";
import {ValidationResultModal} from "./components/validation-result-modal";
import {InvoiceModal} from "./components/invoice-modal";
import {InvoiceAttachmentModal} from "./components/invoice-attachment-modal";

export class InvoiceEdit {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private proxyService = resolve(ProxyService);
    private invoiceService = resolve(InvoiceService);
    private invoiceContext = resolve(InvoiceContext);
    private invoiceCalculator = resolve(InvoiceCalculator);
    private invoiceComposer = resolve(InvoiceComposer);
    private newInvoiceSubscription: IDisposable;
    private newCreditNoteSubscription: IDisposable;

    selectedPaymentMeansCode: number | undefined = 30;
    @bindable readOnly;
    @observable selectedDocumentType = DocumentType.Invoice;
    @observable customerCompanyNumber: undefined | string;
    @bindable invoiceModal: InvoiceModal;
    @bindable invoiceDateModal: InvoicePaymentModal;
    @bindable invoiceCustomerModal: InvoiceCustomerModal;
    @bindable invoicePaymentModal: InvoicePaymentModal;
    @bindable invoiceAttachmentModal: InvoiceAttachmentModal;
    @bindable validationResultModal: ValidationResultModal;

    bound() {
        this.newInvoiceSubscription = this.ea.subscribe('newInvoice', () => this.newInvoice());
        this.newCreditNoteSubscription = this.ea.subscribe('newCreditNote', () => this.newCreditNote());
    }

    unbinding() {
        this.newInvoiceSubscription.dispose();
        this.newCreditNoteSubscription.dispose();
    }

    taxCategories: ClassifiedTaxCategory[] = [
        { ID: "S", Percent: 21, TaxScheme: { ID: 'VAT' } },
        { ID: "S", Percent: 12, TaxScheme: { ID: 'VAT' } },
        { ID: "S", Percent: 6, TaxScheme: { ID: 'VAT' } },
        { ID: "Z", Percent: 0, TaxScheme: { ID: 'VAT' } },
    ];

    paymentMeanCodeMatcher = (a: PaymentMeansCode, b: PaymentMeansCode) => {
        return a?.value === b?.value;
    };

    taxCategoryMatcher = (a: ClassifiedTaxCategory, b: ClassifiedTaxCategory) => {
        return a?.Percent === b?.Percent;
    };

    newInvoice() {
        this.selectedDocumentType = DocumentType.Invoice;
        this.invoiceContext.newUBLDocument();
        this.showCustomerModal();
    }

    newCreditNote() {
        this.selectedDocumentType = DocumentType.CreditNote;
        this.invoiceContext.newUBLDocument(DocumentType.CreditNote);
        this.showCustomerModal();
    }

    calcLineTotal(line: UBLLine) {
        const quantity = getAmount(line);
        line.LineExtensionAmount.value = roundTwoDecimals(line.Price.PriceAmount.value * quantity.value);
        this.invoiceCalculator.calculateTaxAndTotals(this.invoiceContext.selectedInvoice);
    }

    addLine() {
        let line: UBLLine;
        const pos = this.invoiceContext.getNextPosition();
        if (this.selectedDocumentType === DocumentType.Invoice) {
            line = this.invoiceComposer.getInvoiceLine(pos);
        } else {
            line = this.invoiceComposer.getCreditNoteLine(pos);
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
            const xml = this.buildXml();

            const response = await this.invoiceService.validate(xml);
            if (!response.isValid) {
                this.validationResultModal.showModal(response);
                return;
            }

            await this.proxyService.sendDocument(xml);
        } catch(e) {
            console.error(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to update account"});
        } finally {
            this.ea.publish('hideOverlay');
        }
    }

    // downloadPdf() {
    //     if (!this.invoiceContext.selectedInvoice) return;
    //     downloadInvoicePdf(this.invoiceContext.selectedInvoice);
    // }

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
            this.invoiceContext.clearSelectedInvoice();
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Invoice draft saved"});
        } catch(e) {
            console.error(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to save invoice as draft"});
        }
    }

    async deleteDraft() {
        try {
            await this.invoiceService.deleteInvoiceDraft(this.invoiceContext.selectedDraft.id);
            this.invoiceContext.drafts.splice(this.invoiceContext.drafts.findIndex(item => item.id === this.invoiceContext.selectedDraft.id), 1);
            this.invoiceContext.clearSelectedInvoice();
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Invoice draft removed"});
        } catch(e) {
            console.error(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to delete invoice draft"});
        }
    }

    convertInvoiceToDraft() {
        const xml = this.buildXml();
        return {
            id: this.invoiceContext.selectedDraft?.id,
            docType: this.selectedDocumentType,
            docId: this.invoiceContext.selectedInvoice.ID,
            counterPartyName: this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party?.PartyName.Name,
            createdAt: this.invoiceContext.selectedInvoice.IssueDate,
            dueDate: this.invoiceContext.selectedInvoice.DueDate,
            amount: this.invoiceContext.selectedInvoice.LegalMonetaryTotal.LineExtensionAmount.value,
            xml: xml
        } as InvoiceDraftDto;
    }

    downloadUBL() {
        if (!this.invoiceContext.selectedInvoiceXML) {
            this.ea.publish('alert', {alertType: AlertType.Warning, text: "No UBL data available"});
        }
        const blob = new Blob([this.invoiceContext.selectedInvoiceXML], { type: "application/xml" });
        const url = URL.createObjectURL(blob);

        const a = document.createElement("a");
        a.href = url;
        a.download = `${this.invoiceContext.selectedInvoice.ID}.xml`;
        document.body.appendChild(a);
        a.click();
        
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }

    async validate() {
        const form = document.getElementById('invoiceForm') as HTMLFormElement;
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }
        console.log(JSON.stringify(this.invoiceContext.selectedInvoice));
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

    recalculateLinePositions() {
        for (let i = 0; i < this.invoiceContext.lines.length; i++) {
            this.invoiceContext.lines[i].ID = (i + 1).toString();
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
        this.invoiceCustomerModal.showModal(() => {
            if (!this.invoiceContext.selectedInvoice.ID) {
                this.showInvoiceModal();
            }
        });
    }

    showPaymentModal() {
        this.invoicePaymentModal.showModal();
    }

    showAttachmentModal() {
        this.invoiceAttachmentModal.showModal();
    }

    @computed({
        deps: [
            'invoiceContext.selectedInvoice.ID',
            'invoiceContext.selectedInvoice.BuyerReference',
            'invoiceContext.selectedInvoice.IssueDate',
            'invoiceContext.selectedInvoice.DueDate',
            'invoiceContext.selectedInvoice.PaymentTerms',
            'invoiceContext.selectedInvoice.AccountingCustomerParty.Party.PartyIdentification[0].ID.value',
            'invoiceContext.selectedInvoice.AccountingCustomerParty.Party.PartyName.Name',
            'invoiceContext.selectedInvoice.AccountingCustomerParty.PartyTaxScheme.TaxScheme.ID',
            'invoiceContext.selectedInvoice.LegalMonetaryTotal.LineExtensionAmount.value',
            'invoiceContext.selectedInvoice.PaymentMeans.PaymentMeansCode.value',
            'invoiceContext.selectedInvoice.PaymentMeans.PayeeFinancialAccount.ID'
        ] })
    get isValid() {
        const inv = this.invoiceContext.selectedInvoice;
        return inv && inv.ID && inv.BuyerReference && inv.IssueDate && (inv.DueDate || inv.PaymentTerms)
            && inv.AccountingCustomerParty
            && inv.AccountingCustomerParty.Party.PartyIdentification[0].ID.value
            && inv.AccountingCustomerParty.Party.PartyName.Name
            && inv.AccountingCustomerParty.Party.PartyTaxScheme.TaxScheme.ID
            && inv.LegalMonetaryTotal.LineExtensionAmount.value > 0
            && (!inv.PaymentMeans || (inv.PaymentMeans.PaymentMeansCode.value != 30|| (inv.PaymentMeans.PaymentMeansCode.value === 30 && inv.PaymentMeans.PayeeFinancialAccount.ID)))
        ;
    }

    downloadAttachment(attachment: Attachment) {
        if (attachment.EmbeddedDocumentBinaryObject) {
            const source = `data:${attachment.EmbeddedDocumentBinaryObject.__mimeCode};base64,${attachment.EmbeddedDocumentBinaryObject.value}`;
            const link = document.createElement('a');
            document.body.appendChild(link);
            link.href = source;
            link.target = '_self';
            link.download = attachment.EmbeddedDocumentBinaryObject.__filename;
            link.click();
            this.ea.publish('alert', {alertType: AlertType.Info, text: `File '${attachment.EmbeddedDocumentBinaryObject.__filename}' downloaded`});
        }
        if (attachment.ExternalReference && attachment.ExternalReference.URI) {
            window.open(attachment.ExternalReference.URI, '_blank');
        }
    }

}

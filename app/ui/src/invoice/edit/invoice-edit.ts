import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../invoice-context";
import {bindable, computed, IDisposable, IEventAggregator} from "aurelia";
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
import {InvoicePaymentModal} from "./components/invoice-payment-modal";
import {InvoiceCustomerModal} from "./components/invoice-customer-modal";
import {InvoiceCalculator, roundTwoDecimals} from "../invoice-calculator";
import {InvoiceComposer} from "../invoice-composer";
// import {downloadInvoicePdf} from "../pdf/invoice-pdf";
import {InvoiceService, DocumentType} from "../../services/app/invoice-service";
import {ValidationResultModal} from "./components/validation-result-modal";
import {InvoiceModal} from "./components/invoice-modal";
import {InvoiceAttachmentModal} from "./components/invoice-attachment-modal";
import {buildCreditNoteXml, buildInvoiceXml} from "../../services/peppol/ubl-builder";
import {InvoiceNumberModal} from "./components/invoice-number-modal";
import { toErrorResponse } from "../../app/util/error-response-handler";

export class InvoiceEdit {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private invoiceService = resolve(InvoiceService);
    private invoiceContext = resolve(InvoiceContext);
    private invoiceCalculator = resolve(InvoiceCalculator);
    private invoiceComposer = resolve(InvoiceComposer);
    private newInvoiceSubscription: IDisposable;
    private newCreditNoteSubscription: IDisposable;

    @bindable readOnly;
    @bindable selectedDocumentType: DocumentType;
    @bindable invoiceModal: InvoiceModal;
    @bindable invoiceDateModal: InvoicePaymentModal;
    @bindable invoiceCustomerModal: InvoiceCustomerModal;
    @bindable invoicePaymentModal: InvoicePaymentModal;
    @bindable invoiceAttachmentModal: InvoiceAttachmentModal;
    @bindable invoiceNumberModal: InvoiceNumberModal;
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
        this.selectedDocumentType = DocumentType.INVOICE;
        this.invoiceContext.newUBLDocument();
        this.invoiceContext.getLastInvoiceReference();
        this.showCustomerModal();
    }

    newCreditNote() {
        this.selectedDocumentType = DocumentType.CREDIT_NOTE;
        this.invoiceContext.newUBLDocument(DocumentType.CREDIT_NOTE);
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
        if (this.selectedDocumentType === DocumentType.INVOICE) {
            line = this.invoiceComposer.getInvoiceLine(pos);
        } else {
            line = this.invoiceComposer.getCreditNoteLine(pos);
        }
        this.invoiceContext.lines.push(line);
        this.saveAsDraft(false).catch(e => console.error(e));
    }

    deleteLine(line: UBLLine) {
        this.invoiceContext.lines.splice(this.invoiceContext.lines.findIndex(item => item === line), 1);
        this.invoiceCalculator.calculateTaxAndTotals(this.invoiceContext.selectedInvoice);
    }

    async verifyNumberAndSend() {
        if (!this.invoiceContext.selectedInvoice.ID) {
            this.invoiceNumberModal.showModal(() => this.sendInvoice());
        } else {
            await this.sendInvoice();
        }
    }

    async sendInvoice() {
        try {
            this.ea.publish('showOverlay', "Sending invoice");
            const xml = this.buildXml();

            const response = await this.invoiceService.validate(xml);
            if (!response.isValid) {
                this.validationResultModal.showModal(response);
                return;
            }

            await this.invoiceService.createDocument(xml);
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Invoice sent successfully"});
            if (this.invoiceContext.selectedDocument.draftedOn) {
                await this.deleteDraft();
            } else {
                this.invoiceContext.clearSelectedInvoice();
            }
        } catch (e: unknown) {
            const errorResponse = await toErrorResponse(e);
            if (errorResponse?.errorCode === 'INVOICE_NUMBER_ALREADY_USED') {
                this.ea.publish('alert', { alertType: AlertType.Danger, text: 'Invoice number already used' });
            } else if (errorResponse?.message) {
                this.ea.publish('alert', { alertType: AlertType.Danger, text: errorResponse.message });
            } else {
                this.ea.publish('alert', { alertType: AlertType.Danger, text: 'Failed to send invoice' });
            }
        } finally {
            this.ea.publish('hideOverlay');
        }
    }

    // downloadPdf() {
    //     if (!this.invoiceContext.selectedInvoice) return;
    //     downloadInvoicePdf(this.invoiceContext.selectedInvoice);
    // }

    buildXml(): string {
        if  (this.selectedDocumentType === DocumentType.INVOICE)  {
            return buildInvoiceXml(this.invoiceContext.selectedInvoice as Invoice)
        } else {
            return buildCreditNoteXml(this.invoiceContext.selectedInvoice as CreditNote);
        }
    }

    async saveAsDraft(returnToOverview: boolean = true) {
        try {
            const xml = this.buildXml();
            if (this.invoiceContext.selectedDocument) {
                const newDraft = await this.invoiceService.updateDocument(this.invoiceContext.selectedDocument.id, xml, true);
                this.invoiceContext.draftPage.content.splice(this.invoiceContext.draftPage.content.findIndex(item => item.id === newDraft.id), 1, newDraft);
            } else {
                const documentDraftDto = await this.invoiceService.createDocument(xml, true);
                this.invoiceContext.selectedDocument = documentDraftDto;
                this.invoiceContext.draftPage.content.unshift(documentDraftDto);
                this.invoiceContext.draftPage.totalElements++;
            }
            if (returnToOverview) {
                this.invoiceContext.clearSelectedInvoice();
            }
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Invoice draft saved"});
        } catch(e) {
            console.error(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to save invoice as draft"});
        }
    }

    async deleteDraft() {
        try {
            await this.invoiceService.deleteDocument(this.invoiceContext.selectedDocument.id);
            this.invoiceContext.deleteDraft(this.invoiceContext.selectedDocument);
            this.invoiceContext.clearSelectedInvoice();
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Invoice draft removed"});
        } catch(e) {
            console.error(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to delete invoice draft"});
        }
    }

    downloadUBL() {
        if (!this.invoiceContext.selectedDocument) {
            this.ea.publish('alert', {alertType: AlertType.Warning, text: "No UBL data available"});
        }
        const blob = new Blob([this.invoiceContext.selectedDocument.ubl], { type: "application/xml" });
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
        const xml = this.buildXml();
        const response = await this.invoiceService.validate(xml);
        this.validationResultModal.showModal(response);
        console.log(response);
    }

    // customerCompanyNumberChanged(newValue: string) {
    //     this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party.EndpointID.value = newValue;
    //     this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party.PartyIdentification[0].ID.value = newValue;
    //     this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party.PartyTaxScheme.CompanyID = newValue;
    //     console.log(newValue);
    // }

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
            console.log('customer modal closed');
        });
    }

    showPaymentModal() {
        this.invoicePaymentModal.showModal();
    }

    showAttachmentModal() {
        this.invoiceAttachmentModal.showModal();
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

    @computed({
        deps: [
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
        const hasParty = inv && inv.AccountingCustomerParty && inv.AccountingCustomerParty.Party;
        const partyIdentification = hasParty && inv.AccountingCustomerParty.Party.PartyIdentification;
        const hasPartyIdentificationId =
            !partyIdentification ||
            (Array.isArray(partyIdentification) &&
                partyIdentification.length > 0 &&
                partyIdentification[0].ID &&
                partyIdentification[0].ID.value);

        return inv && inv.BuyerReference && inv.IssueDate && (inv.DueDate || inv.PaymentTerms)
            && hasParty
            && hasPartyIdentificationId
            && inv.AccountingCustomerParty.Party.PartyName.Name
            && inv.AccountingCustomerParty.Party.PartyTaxScheme.TaxScheme.ID
            && inv.LegalMonetaryTotal.LineExtensionAmount.value > 0
            && this.isPaymentInfoComplete;
    }

    @computed('invoiceContext.selectedInvoice.AccountingCustomerParty.Party.PartyName.Name')
    get isCustomerInfoComplete(): boolean {
        return !!this.invoiceContext.selectedInvoice?.AccountingCustomerParty?.Party?.PartyName?.Name;
    }

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

}

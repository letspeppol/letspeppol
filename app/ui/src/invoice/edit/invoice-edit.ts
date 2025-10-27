import {resolve} from "@aurelia/kernel";
import {ProxyService} from "../../services/proxy-service";
import {DocumentType, InvoiceContext} from "../invoice-context";
import {bindable, IEventAggregator, observable} from "aurelia";
import {
    getAmount,
    CreditNote,
    Invoice,
    PaymentMeansCode,
    UBLLine, ClassifiedTaxCategory
} from "../../peppol/ubl";
import {AlertType} from "../../alert/alert";
import {buildCreditNote, buildInvoice, parseInvoice} from "../../peppol/ubl-parser";
import {InvoicePaymentModal} from "./components/invoice-payment-modal";
import {InvoiceCustomerModal} from "./components/invoice-customer-modal";
import {InvoiceCalculator, roundTwoDecimals} from "../invoice-calculator";
import {InvoiceComposer} from "../invoice-composer";
import {downloadInvoicePdf} from "../pdf/invoice-pdf";

export class InvoiceEdit {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private proxyService = resolve(ProxyService);
    private invoiceContext = resolve(InvoiceContext);
    private invoiceCalculator = resolve(InvoiceCalculator);
    private invoiceComposer = resolve(InvoiceComposer);
    private documentTypes = Object.values(DocumentType) as string[];
    selectedPaymentMeansCode: number | undefined = 30;
    @observable selectedDocumentType = DocumentType.Invoice;
    @observable customerCompanyNumber: undefined | string;
    @bindable invoicePaymentModal: InvoicePaymentModal;
    @bindable invoiceCustomerModal: InvoiceCustomerModal;

    taxCategories: ClassifiedTaxCategory[] = [
        { ID: "S", Percent: 21, TaxScheme: { ID: 'VAT' } },
        { ID: "S", Percent: 12, TaxScheme: { ID: 'VAT' } },
        { ID: "S", Percent: 6, TaxScheme: { ID: 'VAT' } },
        { ID: "Z", Percent: 0, TaxScheme: { ID: 'VAT' } },
    ];

    paymentMeanCodeMatcher = (a: PaymentMeansCode, b: PaymentMeansCode) => {
        return a?.value === b?.value;
    };

    customerCompanyNumberChanged(newValue: string) {
        this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party.EndpointID.value = newValue;
        this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party.PartyIdentification[0].ID.value = newValue;
        this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party.PartyTaxScheme.CompanyID.value = newValue;
        console.log(newValue);
    }

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

    selectedDocumentTypeChanged(newValue) {
        if (newValue === DocumentType.Invoice) {
            this.invoiceContext.selectedInvoice = this.invoiceComposer.creditNoteToInvoice(this.invoiceContext.selectedInvoice as unknown as CreditNote);
        } else {
            this.invoiceContext.selectedInvoice = this.invoiceComposer.invoiceToCreditNote(this.invoiceContext.selectedInvoice as Invoice);
        }
    }

    paymentMeansCodeChanged() {
        const paymentMeansCode = this.invoiceContext.paymentMeansCodes.find(item => item.value === this.invoiceContext.selectedInvoice.PaymentMeans.PaymentMeansCode.value);
        this.invoiceContext.selectedInvoice.PaymentMeans.PaymentMeansCode.__name = paymentMeansCode.__name;
    }

    async sendInvoice() {
        console.log(JSON.stringify(this.invoiceContext.selectedInvoice));
        let xml = this.buildXml();
        try {
            await this.proxyService.sendDocument(xml);
        } catch(e) {
            console.log(e);
        }
        console.log(xml);
        console.log(parseInvoice(xml));
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

    showPaymentModal() {
        this.invoicePaymentModal.showModal();
    }

    showCustomerModal() {
        this.invoiceCustomerModal.showModal();
    }
}

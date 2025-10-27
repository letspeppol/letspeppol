import {IEventAggregator, observable, singleton} from "aurelia";
import {
    CreditNote,
    CreditNoteLine,
    getLines,
    Invoice,
    InvoiceLine,
    PaymentMeansCode,
    UBLDoc
} from "../services/peppol/ubl";
import {CompanyService} from "../services/app/company-service";
import {resolve} from "@aurelia/kernel";
import {InvoiceComposer} from "./invoice-composer";
import {InvoiceCalculator} from "./invoice-calculator";
import {AlertType} from "../components/alert/alert";
import {InvoiceDraftDto} from "../services/app/invoice-service";

export enum DocumentType {
    Invoice = "invoice",
    CreditNote = "credit-note"
}

// export interface PaymentMeansCode {
//     code: number
//     name: string;
// }

@singleton()
export class InvoiceContext {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly companyService = resolve(CompanyService);
    private readonly invoiceComposer = resolve(InvoiceComposer);
    private readonly invoiceCalculator = resolve(InvoiceCalculator);
    selectedDocumentType: DocumentType = DocumentType.Invoice;
    lines : undefined | InvoiceLine[] | CreditNoteLine[];
    drafts: InvoiceDraftDto[] = [];
    @observable selectedInvoice:  undefined | Invoice | CreditNote;
    selectedDraft: InvoiceDraftDto;

    clearSelectedInvoice() {
        this.selectedInvoice = undefined;
    }

    selectedInvoiceChanged(newValue: UBLDoc) {
        this.lines = getLines(newValue);
    }

    async initCompany() {
        if (!this.companyService.myCompany) {
            try {
                await this.companyService.getAndSetMyCompanyForToken();
                this.newUBLDocument();
            } catch {
                this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to get company info"});
            }
        }
    }

    async newUBLDocument(documentType : DocumentType = DocumentType.Invoice) {
        if (documentType === DocumentType.Invoice) {
            this.selectedInvoice = this.invoiceComposer.createInvoice();
        } else {
            this.selectedInvoice = this.invoiceComposer.createCreditNote();
        }

        const line: InvoiceLine = this.invoiceComposer.getInvoiceLine("1");
        line.InvoicedQuantity.value = 2;
        line.Item.Description = "item";
        line.Price.PriceAmount.value = 5.33;
        line.LineExtensionAmount.value = 10.66;

        const jop = this.selectedInvoice as Invoice;
        jop.ID = "20250001";
        jop.BuyerReference = "PO-12345";
        jop.AccountingCustomerParty.Party.EndpointID.value = "0705969661";
        jop.AccountingCustomerParty.Party.PartyName.Name = "Ponder Source";
        jop.InvoiceLine.push(line);
        jop.PaymentMeans.PayeeFinancialAccount.Name = "Software Oplossing";
        jop.PaymentMeans.PayeeFinancialAccount.ID = "BE123457807";
        jop.PaymentTerms = {Note: "jaja"};
        this.invoiceCalculator.calculateTaxAndTotals(this.selectedInvoice);
    }

    getNextPosition(): string {
        if (this.lines.length && this.lines[this.lines.length - 1]) {
            const id = parseInt(this.lines[this.lines.length - 1].ID);
            if (!isNaN(id)) {
                return (id + 1).toString();
            }
        }
        return "1";
    }

    public paymentMeansCodes: PaymentMeansCode[] = [
        { value: 10, __name: "In Cash"},
        { value: 30, __name: "Credit Transfer"}
    ];

    // Drafts

    addDraft(draft) {
        this.drafts.unshift(draft);
    }

    deleteDraft(draft) {
        const index = this.drafts.findIndex(item => item === draft);
        if (index > -1) {
            this.drafts.splice(index, 1);
        }
    }
}

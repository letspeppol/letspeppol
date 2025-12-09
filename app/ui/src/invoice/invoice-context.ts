import {IEventAggregator, observable, singleton} from "aurelia";
import {CreditNote, CreditNoteLine, getLines, Invoice, InvoiceLine, UBLDoc} from "../services/peppol/ubl";
import {CompanyService} from "../services/app/company-service";
import {resolve} from "@aurelia/kernel";
import {InvoiceComposer} from "./invoice-composer";
import {InvoiceCalculator} from "./invoice-calculator";
import {AlertType} from "../components/alert/alert";
import {DocumentDto, DocumentPageDto, DocumentType} from "../services/app/invoice-service";

@singleton()
export class InvoiceContext {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly companyService = resolve(CompanyService);
    private readonly invoiceComposer = resolve(InvoiceComposer);
    private readonly invoiceCalculator = resolve(InvoiceCalculator);
    lines : undefined | InvoiceLine[] | CreditNoteLine[];
    draftPage: DocumentPageDto = undefined;
    @observable selectedInvoice:  undefined | Invoice | CreditNote;
    selectedDocument: DocumentDto;
    selectedInvoiceXML: string = undefined;
    selectedDocumentType: DocumentType = DocumentType.INVOICE;

    readOnly: boolean = false;

    clearSelectedInvoice() {
        this.selectedInvoice = undefined;
        this.selectedDocument = undefined;
    }

    selectedInvoiceChanged(newValue: UBLDoc) {
        this.lines = getLines(newValue);
    }

    async initCompany() {
        if (!this.companyService.myCompany) {
            try {
                await this.companyService.getAndSetMyCompanyForToken();
            } catch {
                this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to get company info"});
            }
        }
    }

    newUBLDocument(documentType : DocumentType = DocumentType.INVOICE) {
        if (documentType === DocumentType.INVOICE) {
            this.selectedInvoice = this.invoiceComposer.createInvoice();
        } else {
            this.selectedInvoice = this.invoiceComposer.createCreditNote();
        }
        this.invoiceCalculator.calculateTaxAndTotals(this.selectedInvoice);
        this.readOnly = false;
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

    // Drafts
    
    deleteDraft(draft) {
        const index = this.draftPage.content.findIndex(item => item === draft);
        if (index > -1) {
            this.draftPage.content.splice(index, 1);
        }
    }
}

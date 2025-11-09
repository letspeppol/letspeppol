import {IEventAggregator, observable, singleton} from "aurelia";
import {CreditNote, CreditNoteLine, getLines, Invoice, InvoiceLine, UBLDoc} from "../services/peppol/ubl";
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

@singleton()
export class InvoiceContext {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly companyService = resolve(CompanyService);
    private readonly invoiceComposer = resolve(InvoiceComposer);
    private readonly invoiceCalculator = resolve(InvoiceCalculator);
    lines : undefined | InvoiceLine[] | CreditNoteLine[];
    drafts: InvoiceDraftDto[] = [];
    @observable selectedInvoice:  undefined | Invoice | CreditNote;
    selectedDraft: InvoiceDraftDto;
    selectedInvoiceXML: string = undefined;
    readOnly: boolean = false;

    clearSelectedInvoice() {
        this.selectedInvoice = undefined;
        this.selectedDraft = undefined;
        this.selectedInvoiceXML = undefined;
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

    newUBLDocument(documentType : DocumentType = DocumentType.Invoice) {
        if (documentType === DocumentType.Invoice) {
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
        const index = this.drafts.findIndex(item => item === draft);
        if (index > -1) {
            this.drafts.splice(index, 1);
        }
    }
}

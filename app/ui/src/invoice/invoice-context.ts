import {IEventAggregator, observable, singleton} from "aurelia";
import {CreditNote, CreditNoteLine, getLines, Invoice, InvoiceLine, Party, UBLDoc} from "../services/peppol/ubl";
import {CompanyService} from "../services/app/company-service";
import {InvoiceComposer} from "./invoice-composer";
import {InvoiceCalculator} from "./invoice-calculator";
import {AlertType} from "../components/alert/alert";
import {DocumentDirection, DocumentDto, DocumentPageDto, DocumentType} from "../services/app/invoice-service";
import {parseCreditNote, parseInvoice} from "../services/peppol/ubl-parser";
import {PartnerDto, PartnerService} from "../services/app/partner-service";
import {resolve} from "@aurelia/kernel";

@singleton()
export class InvoiceContext {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly companyService = resolve(CompanyService);
    private readonly partnerService = resolve(PartnerService);
    private readonly invoiceComposer = resolve(InvoiceComposer);
    private readonly invoiceCalculator = resolve(InvoiceCalculator);
    // Overview
    draftPage: DocumentPageDto = undefined;
    invoicePage: DocumentPageDto = undefined;
    // Current invoice
    lines : undefined | InvoiceLine[] | CreditNoteLine[];
    @observable selectedInvoice:  undefined | Invoice | CreditNote;
    selectedDocument: DocumentDto;
    selectedDocumentType: DocumentType = DocumentType.INVOICE;
    lastInvoiceReference: string = undefined;
    nextInvoiceReference: string = undefined;
    readOnly: boolean = false;
    partnerMissing: boolean = false;

    clearSelectedInvoice() {
        history.replaceState({}, '', `/invoices`);
        this.selectedInvoice = undefined;
        this.selectedDocument = undefined;
    }

    selectedInvoiceChanged(newValue: UBLDoc) {
        if (!newValue) {
            return;
        }
        this.lines = getLines(newValue);
    }

    selectInvoice(item: DocumentDto) {
        this.readOnly = (item.direction === DocumentDirection.INCOMING || item.proxyOn != null);
        this.selectedDocument = item;
        if (item.draftedOn) {
            this.getLastInvoiceReference();
        }
        if (item.type === DocumentType.CREDIT_NOTE) {
            this.selectedInvoice = parseCreditNote(item.ubl);
        } else {
            this.selectedInvoice = parseInvoice(item.ubl);
        }
        if (this.readOnly) {
            this.partnerMissing = true;
            this.partnerService.searchPartners({peppolId: item.partnerPeppolId})
                .then((list) => this.partnerMissing = list.length === 0);
        }
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

    getLastInvoiceReference() {
        this.companyService.getAndSetMyCompanyForToken().then(company => {
            this.lastInvoiceReference = company.lastInvoiceReference;
            this.nextInvoiceReference = this.computeNextInvoiceReference(this.lastInvoiceReference);
        }).catch(() => {
            this.lastInvoiceReference = undefined;
            this.nextInvoiceReference = undefined;
        });
    }

    private computeNextInvoiceReference(lastRef?: string): string {
        if (!lastRef) {
            const year = new Date().getFullYear().toString();
            return `${year}0001`;
        }

        const match = lastRef.match(/(.*?)(\d+)([^0-9]*)$/);
        if (!match) {
            // No digits found
            return undefined;
        }

        const prefix = match[1];
        const numStr = match[2];
        const suffix = match[3];

        const width = numStr.length;
        const nextNum = (parseInt(numStr, 10) + 1).toString().padStart(width, "0");

        return `${prefix}${nextNum}${suffix}`;
    }

    public mapPartner(party: Party): PartnerDto {
        return {
            vatNumber: party.PartyTaxScheme?.CompanyID?.value,
            name: party.PartyName?.Name,
            peppolId: `${party.EndpointID.__schemeID}:${party.EndpointID.value}`,
            customer: true,
            registeredOffice: {
                city: party?.PostalAddress?.CityName,
                postalCode: party?.PostalAddress?.PostalZone,
                street: party?.PostalAddress?.StreetName,
                countryCode: party?.PostalAddress?.Country.IdentificationCode
            }
        } as PartnerDto;
    }

    // Drafts
    
    deleteDraft(draft: DocumentDto) {
        const index = this.draftPage.content.findIndex(item => item.id === draft.id);
        if (index > -1) {
            this.draftPage.content.splice(index, 1);
            this.draftPage.totalElements--;
        }
    }
}

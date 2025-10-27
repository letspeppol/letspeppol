import {observable, singleton} from "aurelia";
import {
    Invoice,
    InvoiceLine, PaymentMeansCode, CreditNote, CreditNoteLine, UBLDoc, getLines
} from "../peppol/ubl";
import {CompanyService} from "../services/company-service";
import {resolve} from "@aurelia/kernel";
import moment from "moment";
import {InvoiceComposer} from "./invoice-composer";
import {InvoiceCalculator} from "./invoice-calculator";

export enum DocumentType {
    Invoice = "Invoice",
    CreditNote = "Credit Note"
}

// export interface PaymentMeansCode {
//     code: number
//     name: string;
// }

@singleton()
export class InvoiceContext {
    private companyService = resolve(CompanyService);
    private invoiceComposer = resolve(InvoiceComposer);
    private invoiceCalculator = resolve(InvoiceCalculator);
    invoices : undefined | Invoice[] | CreditNote[];
    lines : undefined | InvoiceLine[] | CreditNoteLine[];
    @observable selectedInvoice:  undefined | Invoice | CreditNote;

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
            } catch {
                this.companyService.myCompany = {
                    id: 1,
                    companyNumber: "1234",
                    postalCode: "3500",
                    city: "city",
                    street: "street",
                    name: "My company"
                };
            }
        }
    }

    async newUBLDocument(documentType : DocumentType = DocumentType.Invoice) {
        await this.initCompany();
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
        jop.ID = "INV-2025-0001";
        jop.BuyerReference = "PO-12345";
        jop.AccountingCustomerParty.Party.EndpointID.value = "0705969661";
        jop.AccountingCustomerParty.Party.PartyName.Name = "Ponder Source";
        jop.InvoiceLine.push(line);
        jop.PaymentMeans.PayeeFinancialAccount.Name = "Software Oplossing";
        jop.PaymentMeans.PayeeFinancialAccount.ID = "BE123457807";
        this.invoiceCalculator.calculateTaxAndTotals(this.selectedInvoice);
    }

    getNextPosition(): string {
        return "1";
    }

    public paymentMeansCodes: PaymentMeansCode[] = [
        { value: 10, __name: "In Cash"},
        { value: 30, __name: "Credit Transfer"}
    ];
}

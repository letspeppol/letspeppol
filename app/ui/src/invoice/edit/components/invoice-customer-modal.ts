import {bindable} from "aurelia";
import {Party} from "../../../peppol/ubl";

export class InvoiceCustomerModal {
    @bindable invoiceContext;
    open = false;
    customer: Party | undefined;

    vatChanged() {
        this.customer.EndpointID.value = this.customer.PartyIdentification[0].ID.value;
        this.customer.PartyTaxScheme.CompanyID.value = `BE${this.customer.PartyIdentification[0].ID.value}`
    }

    showModal() {
        this.customer = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party));
        this.open = true;
    }

    closeModal() {
        this.open = false;
        this.customer = undefined;
    }

    saveCustomer() {
        this.open = false;
        this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party = this.customer;
    }
}
import {bindable} from "aurelia";
import {Party} from "../../../services/peppol/ubl";
import {PartnerDto} from "../../../services/app/partner-service";
import {CustomerSearch} from "./customer-search";

export class InvoiceCustomerModal {
    @bindable invoiceContext;
    @bindable customerSearch: CustomerSearch;
    open = false;
    customer: Party | undefined;
    customerSavedFunction: () => void;

    vatChanged() {
        if (!this.customer) return;
        this.customer.EndpointID.value = this.customer.PartyIdentification[0].ID.value;
        this.customer.PartyTaxScheme.CompanyID.value = `BE${this.customer.PartyIdentification[0].ID.value}`;
    }

    showModal(customerSavedFunction: () => void) {
        this.customer = JSON.parse(JSON.stringify(this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party));
        this.open = true;
        this.customerSearch.resetSearch();
        this.customerSearch.focusInput();
        this.customerSavedFunction = customerSavedFunction;
    }

    closeModal() {
        this.open = false;
        this.customer = undefined;
        this.customerSearch.resetSearch();
    }

    saveCustomer() {
        this.open = false;
        this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party = this.customer;
        this.customerSearch.resetSearch();
        if (this.customerSavedFunction) {
            this.customerSavedFunction();
        }
    }

    selectCustomer(c: PartnerDto) {
        this.customer = this.toParty(c);
    }

    private toParty(c: PartnerDto): Party {
        const companyNumber = c.companyNumber || '';
        return {
            EndpointID: { value: companyNumber },
            PartyIdentification: [{ ID: { value: companyNumber } }],
            PartyName: { Name: c.name },
            PostalAddress: {
                StreetName: [c.registeredOffice?.street, c.registeredOffice?.houseNumber].filter(Boolean).join(' '),
                CityName: c.registeredOffice?.city,
                PostalZone: c.registeredOffice?.postalCode,
                Country: { IdentificationCode: 'BE' }
            },
            PartyTaxScheme: { CompanyID: { value: `BE${companyNumber}` }, TaxScheme: { ID: 'VAT' } },
            PartyLegalEntity: { RegistrationName: c.name, CompanyID: { value: companyNumber } },
            Contact: { Name: c.paymentAccountName }
        };
    }
}

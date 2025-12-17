import {bindable, observable} from "aurelia";
import {Party} from "../../../services/peppol/ubl";
import {PartnerDto} from "../../../services/app/partner-service";
import {CustomerSearch} from "./customer-search";
import {countryListAlpha2} from "../../../app/countries"
import {isIso6523Scheme} from "../../../app/util/iso6523list";

export class InvoiceCustomerModal {
    @bindable invoiceContext;
    @bindable customerSearch: CustomerSearch;
    @observable peppolId: string;
    open = false;
    customer: Party | undefined;
    customerSavedFunction: () => void;
    private countryList = countryListAlpha2;

    vatChanged() {
        if (!this.customer) return;
        if (this.customer.PartyTaxScheme.CompanyID.value) {
            this.customer.PartyTaxScheme.CompanyID.value = this.customer.PartyTaxScheme.CompanyID.value.toUpperCase();
        }
    }

    nameChanged() {
        this.customer.PartyLegalEntity.RegistrationName = this.customer.PartyName.Name;
    }

    showModal(customerSavedFunction: () => void) {
        this.customer = structuredClone(this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party);
        if (this.customer && this.customer.EndpointID.__schemeID && this.customer.EndpointID.value) {
            this.peppolId = `${this.customer.EndpointID.__schemeID}:${this.customer.EndpointID.value}`;
        } else {
            this.peppolId = undefined;
        }
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
        console.log(this.invoiceContext.selectedInvoice.AccountingCustomerParty.Party);
    }

    selectMatchFunction(name: string, participantID: string) {
        this.peppolId = participantID;
        if (!this.customer.PartyName.Name) {
            this.customer.PartyName.Name = name;
        }
        this.customer.PartyLegalEntity.RegistrationName = this.customer.PartyName.Name;
        this.peppolIdChangedFunction(participantID);
    }

    peppolIdChangedFunction(peppolId: string) {
        if (peppolId.includes(":")) {
            const parts = peppolId.split(":");
            this.customer.EndpointID.__schemeID = parts[0];
            this.customer.EndpointID.value = parts[1];
            this.customer.PartyIdentification = [{ ID: { __schemeID: parts[0], value: parts[1] } }];
            if (!this.customer.PartyLegalEntity.CompanyID || !this.customer.PartyLegalEntity.CompanyID.value) {
                this.customer.PartyLegalEntity.CompanyID = { value: undefined};
            }
            if (parts[0] === '0208') {
                this.customer.PartyLegalEntity.CompanyID.value = parts[1];
                this.vatChanged();
            } else if (parts[0] === '9925') {
                this.customer.PartyLegalEntity.CompanyID.value = parts[1].toUpperCase();
                this.vatChanged();
            }
        }
    }

    selectCustomer(c: PartnerDto) {
        this.peppolId = c.peppolId;
        let scheme = undefined;
        let identifier = undefined;
        if (this.peppolId.includes(":")) {
            const parts = this.peppolId.split(":");
            scheme = parts[0];
            identifier = parts[1];
        }
        this.customer = this.toParty(c, scheme, identifier);
    }

    private toParty(c: PartnerDto, scheme: string, identifier: string): Party {
        const party = {
            EndpointID: { __schemeID: scheme, value: identifier },
            PartyName: { Name: c.name },
            PostalAddress: {
                StreetName: c.registeredOffice?.street,
                AdditionalStreetName: c.registeredOffice?.houseNumber,
                CityName: c.registeredOffice?.city,
                PostalZone: c.registeredOffice?.postalCode,
                Country: { IdentificationCode: 'BE' }
            },
            PartyTaxScheme: { CompanyID: {value: c.vatNumber } , TaxScheme: { ID: 'VAT' } },
            PartyLegalEntity: { RegistrationName: c.name, CompanyID: { value: c.vatNumber } },
            Contact: { Name: c.paymentAccountName }
        } as Party;
        if (isIso6523Scheme(scheme)) {
            party.PartyIdentification = [{ ID: { __schemeID: scheme, value: identifier } }];
        }
        return party;
    }
}

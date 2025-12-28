import {bindable, IEventAggregator} from "aurelia";
import {Party} from "../../../../services/peppol/ubl";
import {PartnerDto, PartnerService} from "../../../../services/app/partner-service";
import {CustomerSearch} from "../customer-search";
import {countryListAlpha2} from "../../../../app/countries"
import {isIso6523Scheme} from "../../../../app/util/iso6523list";
import {normalizeVatNumber} from "../../../../partner/vat-normalizer";
import {KycCompanyResponse} from "../../../../services/kyc/registration-service";
import {resolve} from "@aurelia/kernel";
import {CompanySearchService} from "../../../../services/kyc/company-search-service";
import {AlertType} from "../../../../components/alert/alert";
import {InvoiceContext} from "../../../invoice-context";

export class InvoiceCustomerModal {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly companySearchService = resolve(CompanySearchService);
    private readonly partnerService = resolve(PartnerService);
    private countryList = countryListAlpha2;
    @bindable invoiceContext: InvoiceContext;
    @bindable customerSearch: CustomerSearch;
    peppolId: string;
    open = false;
    saveAsPartner = false;
    customer: Party | undefined;
    customerSavedFunction: () => void;

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
        this.saveAsPartner = false;
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
        if (this.saveAsPartner) {
            const partner = this.invoiceContext.mapPartner(this.customer);
            this.partnerService.createPartner(partner)
                .then(() => this.ea.publish('alert', {alertType: AlertType.Success, text: "Partner created"}))
                .catch(() => this.ea.publish('alert', {alertType: AlertType.Danger, text: "Partner creation failed"}));
        }
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
            if (isIso6523Scheme(parts[0])) {
                this.customer.PartyIdentification = [{ ID: { __schemeID: parts[0], value: parts[2] } }];
            } else {
                this.customer.PartyIdentification = undefined;
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

    peppolIdChanged() {
        if (this.peppolId && this.peppolId.length === 15 && this.peppolId.startsWith('0208:')) {
            this.companySearchService.searchCompany({peppolId: this.peppolId}).then(companies => {
                if (companies.length) {
                    this.completeCustomerInfo(companies[0]);
                    return;
                }
            });
        }
        this.peppolIdChangedFunction(this.peppolId);
    }

    vatNumberChanged() {
        const {normalized, isValidShape} = normalizeVatNumber(this.customer?.PartyTaxScheme?.CompanyID?.value);
        this.customer.PartyTaxScheme.CompanyID.value = normalized;

        // BE VAT
        if (isValidShape && normalized.length === 12) {
            this.companySearchService.searchCompany({vatNumber: normalized}).then(companies => {
                if (companies.length) {
                    this.completeCustomerInfo(companies[0]);
                }
            });
        }
    }

    private completeCustomerInfo(kycCompanyResponse: KycCompanyResponse) {
        if (!this.customer.PartyTaxScheme.CompanyID) {
            this.customer.PartyTaxScheme.CompanyID = {value: undefined};
        }
        if (!this.customer.PartyTaxScheme.CompanyID.value) {
            this.customer.PartyTaxScheme.CompanyID.value = kycCompanyResponse.vatNumber;
        }
        if (!this.peppolId) {
            this.peppolId = kycCompanyResponse.peppolId;
            this.peppolIdChangedFunction(this.peppolId);
        }
        if (!this.customer.PartyName.Name) {
            this.customer.PartyName.Name = kycCompanyResponse.name;
        }
        if (!this.customer.PostalAddress.CityName) {
            this.customer.PostalAddress.CityName = kycCompanyResponse.city;
        }
        if (!this.customer.PostalAddress.PostalZone) {
            this.customer.PostalAddress.PostalZone = kycCompanyResponse.postalCode;
        }
        if (!this.customer.PostalAddress.StreetName) {
            this.customer.PostalAddress.StreetName = kycCompanyResponse.street;
        }
    }
}

import {AlertType} from "../components/alert/alert";
import {IEventAggregator} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {PartnerService} from "../services/app/partner-service";
import {PartnerContext} from "./partner-context";
import {Account} from "../account/account";
import {countryListAlpha2} from "../app/countries"
import {normalizeVatNumber} from "./vat-normalizer";
import {CompanySearchService} from "../services/kyc/company-search-service";
import {KycCompanyResponse} from "../services/kyc/registration-service";

export class PartnerEdit {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly companySearchService = resolve(CompanySearchService);
    private readonly partnerService = resolve(PartnerService);
    private readonly partnerContext = resolve(PartnerContext);
    private countryList = countryListAlpha2;

    async savePartner() {
        try {
            let successMessage = "Partner updated successfully";
            if (this.partnerContext.selectedPartner.id) {
                await this.partnerService.updatePartner(this.partnerContext.selectedPartner.id, this.partnerContext.selectedPartner);
            } else {
                const partner = await this.partnerService.createPartner(this.partnerContext.selectedPartner);
                this.partnerContext.addPartner(partner);
                successMessage = "Partner created successfully";
            }
            this.ea.publish('alert', {alertType: AlertType.Success, text: successMessage});
            this.partnerContext.selectedPartner = undefined;
        } catch(e) {
            console.error(e);
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to update account"});
        }
    }

    getPaymentTerms() {
        return Account.PAYMENT_TERMS;
    }

    peppolIdChanged() {
        const partner = this.partnerContext.selectedPartner;
        if (!partner) return;

        if (partner.peppolId.length === 15 && partner.peppolId.startsWith('0208:')) {
            this.companySearchService.searchCompany({peppolId: partner.peppolId}).then(companies => {
                if (companies.length) {
                    this.completePartnerInfo(companies[0]);
                }
            });
        }
    }

    vatNumberChanged() {
        const partner = this.partnerContext.selectedPartner;
        if (!partner) return;

        const {normalized, isValidShape} = normalizeVatNumber(partner.vatNumber);
        partner.vatNumber = normalized;

        // BE VAT
        if (isValidShape && normalized.length === 12) {
            this.companySearchService.searchCompany({vatNumber: normalized}).then(companies => {
                if (companies.length) {
                    this.completePartnerInfo(companies[0]);
                }
            });
        }
    }

    private completePartnerInfo(kycCompanyResponse: KycCompanyResponse) {
        const partner = this.partnerContext.selectedPartner;
        if (!partner.vatNumber) {
            partner.vatNumber = kycCompanyResponse.vatNumber;
        }
        if (!partner.peppolId) {
            partner.peppolId = kycCompanyResponse.peppolId;
        }
        if (!partner.name) {
            partner.name = kycCompanyResponse.name;
        }
        if (!partner.registeredOffice.city) {
            partner.registeredOffice.city = kycCompanyResponse.city;
        }
        if (!partner.registeredOffice.postalCode) {
            partner.registeredOffice.postalCode = kycCompanyResponse.postalCode;
        }
        if (!partner.registeredOffice.street) {
            partner.registeredOffice.street = kycCompanyResponse.street;
        }
    }
}

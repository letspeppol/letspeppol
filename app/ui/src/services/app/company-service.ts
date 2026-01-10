import {resolve} from '@aurelia/kernel';
import {singleton} from "aurelia";
import {AppApi} from "./app-api";

export interface CompanyDto {
    peppolId: string,
    vatNumber: string,
    name: string,
    displayName: string,
    subscriber: string,
    subscriberEmail: string,
    paymentTerms: string,
    iban: string,
    paymentAccountName: string,
    lastInvoiceReference: string,
    peppolActive: boolean,
    registeredOffice: Address
}

export interface Address {
    city?: string,
    postalCode?: string,
    street?: string,
    houseNumber?: string,
    countryCode?: string
}

@singleton()
export class CompanyService {
    private appApi = resolve(AppApi);
    public myCompany: CompanyDto;

    async getAndSetMyCompanyForToken() : Promise<CompanyDto> {
        this.myCompany = await this.appApi.httpClient.get(`/sapi/company`).then(response => response.json());
        return Promise.resolve(this.myCompany);
    }

    async updateCompany(company: CompanyDto) {
        const response = await this.appApi.httpClient.put(`/sapi/company`, JSON.stringify(company) );
        this.myCompany = await response.json();
        return Promise.resolve(this.myCompany);
    }
}

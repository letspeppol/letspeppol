import {resolve} from '@aurelia/kernel';
import {singleton} from "aurelia";
import {AppApi} from "./app-api";

export interface CompanyDto {
    name: string,
    companyNumber: string,
    vatNumber: string,
    subscriber: string,
    subscriberEmail: string,
    paymentTerms: string,
    iban: string,
    paymentAccountName: string,
    registeredOnPeppol: boolean,
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
        this.myCompany = await this.appApi.httpClient.get(`/api/company`).then(response => response.json());
        return Promise.resolve(this.myCompany);
    }

    async updateCompany(company: CompanyDto) {
        const response = await this.appApi.httpClient.put(`/api/company`, JSON.stringify(company) );
        this.myCompany = await response.json();
        return Promise.resolve(this.myCompany);
    }

}

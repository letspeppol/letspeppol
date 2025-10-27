import { resolve } from '@aurelia/kernel';
import {singleton} from "aurelia";
import {AppApi} from "./api/app-api";

export interface CompanyResponse {
    companyNumber: string,
    name: string,
    subscriber: string,
    subscriberEmail: string,
    paymentTerms: string,
    iban: string,
    paymentAccountName: string,
    registeredOffice: Address
}

export interface Address {
    city: string,
    postalCode: string,
    street: string,
    houseNumber: string
}

@singleton()
export class CompanyService {
    public appApi = resolve(AppApi);
    public myCompany: CompanyResponse;

    async getAndSetMyCompanyForToken() : Promise<CompanyResponse> {
        this.myCompany = await this.appApi.httpClient.get(`/api/company`).then(response => response.json());
        return Promise.resolve(this.myCompany);
    }

}
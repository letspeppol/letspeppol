import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {Address} from "./company-service";
import {AppApi} from "./app-api";

export interface CustomerResponse {
    name: string,
    companyNumber?: string,
    iban?: string,
    paymentAccountName?: string,
    registeredOffice?: Address
}

export interface PartnerDto {
    id?: number,
    companyNumber?: string,
    name: string,
    email?: string,
    customer: boolean,
    supplier: boolean,

    paymentTerms?: string,
    iban?: string,
    paymentAccountName?: string

    registeredOffice?: Address
}

@singleton()
export class PartnerService {
    private appApi = resolve(AppApi);

    async getPartners() : Promise<PartnerDto[]> {
        return await this.appApi.httpClient.get('/api/partner').then(response => response.json());
    }

    async createPartner(partner: PartnerDto) : Promise<PartnerDto> {
        return await this.appApi.httpClient.post('/api/partner', JSON.stringify(partner)).then(response => response.json());
    }

    async updatePartner(id: number, partner: PartnerDto) : Promise<PartnerDto> {
        return await this.appApi.httpClient.put(`/api/partner/${id}`, JSON.stringify(partner)).then(response => response.json());
    }

    async deletePartner(id:number) {
        return await this.appApi.httpClient.delete(`/api/partner/${id}`);
    }
}

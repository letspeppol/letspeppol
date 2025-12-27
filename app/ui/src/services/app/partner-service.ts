import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {Address} from "./company-service";
import {AppApi} from "./app-api";
import {KycCompanyResponse} from "../kyc/registration-service";
import {CompanySearchParams} from "../kyc/company-search-service";

export interface PartnerDto {
    id?: number,
    vatNumber?: string,
    name: string,
    email?: string,
    peppolId: string,
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

    async searchPartners(params: {peppolId: string}): Promise<PartnerDto[]> {
        const qs = new URLSearchParams();

        if (params.peppolId?.trim()) qs.set(`peppolId`, params.peppolId.trim());

        const url = qs.toString() ? `/sapi/partner/search?${qs.toString()}` : `/sapi/company/search`;
        const response = await this.appApi.httpClient.get(url);

        return response.json();
    }

    async getPartners() : Promise<PartnerDto[]> {
        return await this.appApi.httpClient.get('/sapi/partner').then(response => response.json());
    }

    async createPartner(partner: PartnerDto) : Promise<PartnerDto> {
        return await this.appApi.httpClient.post('/sapi/partner', JSON.stringify(partner)).then(response => response.json());
    }

    async updatePartner(id: number, partner: PartnerDto) : Promise<PartnerDto> {
        return await this.appApi.httpClient.put(`/sapi/partner/${id}`, JSON.stringify(partner)).then(response => response.json());
    }

    async deletePartner(id:number) {
        return await this.appApi.httpClient.delete(`/sapi/partner/${id}`);
    }
}

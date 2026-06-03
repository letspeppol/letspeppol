import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {Address} from "./company-service";
import {AppApi} from "./app-api";
import {CacheTtl} from "./cache-config";

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
    private partnersCache?: { value: PartnerDto[], expiresAt: number };
    private partnersRequest?: Promise<PartnerDto[]>;

    async searchPartners(params: {peppolId: string}): Promise<PartnerDto[]> {
        const qs = new URLSearchParams();

        if (params.peppolId?.trim()) qs.set(`peppolId`, params.peppolId.trim());

        const url = qs.toString() ? `/sapi/partner/search?${qs.toString()}` : `/sapi/partner/search`;
        const response = await this.appApi.httpClient.get(url);

        return response.json();
    }

    async getPartners() : Promise<PartnerDto[]> {
        if (this.partnersCache && this.partnersCache.expiresAt > Date.now()) {
            return this.partnersCache.value;
        }
        if (this.partnersRequest) {
            return this.partnersRequest;
        }
        this.partnersRequest = this.appApi.httpClient.get('/sapi/partner')
            .then(response => response.json())
            .then((partners: PartnerDto[]) => {
                this.partnersCache = {value: partners, expiresAt: Date.now() + CacheTtl.partners};
                return partners;
            })
            .finally(() => this.partnersRequest = undefined);
        return this.partnersRequest;
    }

    async createPartner(partner: PartnerDto) : Promise<PartnerDto> {
        const created = await this.appApi.httpClient.post('/sapi/partner', JSON.stringify(partner)).then(response => response.json());
        this.clearCache();
        return created;
    }

    async updatePartner(id: number, partner: PartnerDto) : Promise<PartnerDto> {
        const updated = await this.appApi.httpClient.put(`/sapi/partner/${id}`, JSON.stringify(partner)).then(response => response.json());
        this.clearCache();
        return updated;
    }

    async deletePartner(id:number) {
        const response = await this.appApi.httpClient.delete(`/sapi/partner/${id}`);
        this.clearCache();
        return response;
    }

    clearCache() {
        this.partnersCache = undefined;
        this.partnersRequest = undefined;
    }
}

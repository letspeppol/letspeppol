import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {Address} from "./company-service";
import {AppApi} from "./app-api";
import {CacheTtl} from "./cache-config";
import {OwnershipService} from "./ownership-service";

export interface PartnerDto {
    id?: number,
    identifier?: string,
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

interface PartnersRequest {
    peppolId: string | null,
    partners: Promise<PartnerDto[]>
}

@singleton()
export class PartnerService {
    private appApi = resolve(AppApi);
    private ownershipService = resolve(OwnershipService);
    private partnersCache?: { peppolId: string | null, value: PartnerDto[], expiresAt: number };
    private partnersRequest?: PartnersRequest;

    async searchPartners(params: {peppolId: string}): Promise<PartnerDto[]> {
        const qs = new URLSearchParams();

        if (params.peppolId?.trim()) qs.set(`peppolId`, params.peppolId.trim());

        const url = qs.toString() ? `/sapi/partner/search?${qs.toString()}` : `/sapi/partner/search`;
        const response = await this.appApi.httpClient.get(url);

        return response.json();
    }

    async getPartners() : Promise<PartnerDto[]> {
        const peppolId = this.ownershipService.getCurrentPeppolId();
        if (this.partnersCache?.peppolId === peppolId && this.partnersCache.expiresAt > Date.now()) {
            return this.partnersCache.value;
        }
        if (this.partnersRequest?.peppolId === peppolId) {
            return this.partnersRequest.partners;
        }
        const request: PartnersRequest = {
            peppolId,
            partners: this.appApi.httpClient.get('/sapi/partner')
                .then(response => response.json())
                .then((partners: PartnerDto[]) => {
                    if (this.partnersRequest === request) {
                        this.partnersCache = {peppolId, value: partners, expiresAt: Date.now() + CacheTtl.partners};
                    }
                    return partners;
                })
                .finally(() => {
                    if (this.partnersRequest === request) {
                        this.partnersRequest = undefined;
                    }
                }),
        };
        this.partnersRequest = request;
        return request.partners;
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

import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {AppApi} from "./app-api";

export interface SponsorsDto {
    sponsors: SponsorDto[]
}

export interface SponsorDto {
    name: string,
    logoUrl: string,
    url: string
}

export interface SponsorInvoiceRequest {
    amount: number,
    currency: string,
    name: string,
    message: string
}

export interface SponsorInvoiceResponse {
    status: string,
    message: string
}

export interface SponsorContributionDto {
    name: string,
    message: string,
    amount: number,
    currency: string,
    date: string
}

@singleton()
export class SponsorService {
    private appApi = resolve(AppApi);

    async getSponsors() : Promise<SponsorsDto> {
        return await this.appApi.httpClient.get('/api/sponsors').then(response => response.json());
    }

    async sponsor(request: SponsorInvoiceRequest) : Promise<SponsorInvoiceResponse> {
        return await this.appApi.httpClient.post('/sapi/sponsors', JSON.stringify(request), {
            headers: {
                'Content-Type': 'application/json'
            }
        }).then(response => response.json());
    }

    async getSponsorContributions() : Promise<SponsorContributionDto[]> {
        return await this.appApi.httpClient.get('/api/sponsors/contributions').then(response => response.json());
    }
}

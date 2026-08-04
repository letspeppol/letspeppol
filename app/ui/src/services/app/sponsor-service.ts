import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {AppApi} from "./app-api";
import {SponsorContributionDto, StatisticsService} from "./statistics-service";
import {CacheTtl} from "./cache-config";

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

@singleton()
export class SponsorService {
    private appApi = resolve(AppApi);
    private statisticsService = resolve(StatisticsService);
    private sponsorsCache?: { value: SponsorsDto, expiresAt: number };
    private sponsorsRequest?: Promise<SponsorsDto>;
    private contributionsCache?: { value: SponsorContributionDto[], expiresAt: number };
    private contributionsRequest?: Promise<SponsorContributionDto[]>;

    async getSponsors() : Promise<SponsorsDto> {
        if (this.sponsorsCache && this.sponsorsCache.expiresAt > Date.now()) {
            return this.sponsorsCache.value;
        }
        if (this.sponsorsRequest) {
            return this.sponsorsRequest;
        }
        this.sponsorsRequest = this.appApi.httpClient.get('/api/sponsors')
            .then(response => response.json())
            .then((sponsors: SponsorsDto) => {
                this.sponsorsCache = {value: sponsors, expiresAt: Date.now() + CacheTtl.sponsors};
                return sponsors;
            })
            .finally(() => this.sponsorsRequest = undefined);
        return this.sponsorsRequest;
    }

    async sponsor(request: SponsorInvoiceRequest) : Promise<SponsorInvoiceResponse> {
        const response = await this.appApi.httpClient.post('/sapi/sponsors', JSON.stringify(request), {
            headers: {
                'Content-Type': 'application/json'
            }
        }).then(response => response.json());
        this.clearContributionsCache();
        this.statisticsService.clearDonationStatsCache();
        return response;
    }

    async getSponsorContributions() : Promise<SponsorContributionDto[]> {
        if (this.contributionsCache && this.contributionsCache.expiresAt > Date.now()) {
            return this.contributionsCache.value;
        }
        if (this.contributionsRequest) {
            return this.contributionsRequest;
        }
        this.contributionsRequest = this.appApi.httpClient.get('/api/sponsors/contributions')
            .then(response => response.json())
            .then((contributions: SponsorContributionDto[]) => {
                this.contributionsCache = {value: contributions, expiresAt: Date.now() + CacheTtl.sponsorContributions};
                return contributions;
            })
            .finally(() => this.contributionsRequest = undefined);
        return this.contributionsRequest;
    }

    clearContributionsCache() {
        this.contributionsCache = undefined;
        this.contributionsRequest = undefined;
    }

    clearCache() {
        this.sponsorsCache = undefined;
        this.sponsorsRequest = undefined;
        this.clearContributionsCache();
    }
}

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

@singleton()
export class SponsorService {
    private appApi = resolve(AppApi);

    async getSponsors() : Promise<SponsorsDto> {
        return await this.appApi.httpClient.get('/api/sponsors').then(response => response.json());
    }
}

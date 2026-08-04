import {resolve} from "@aurelia/kernel";
import {SponsorService} from "../services/app/sponsor-service";
import {SponsorContributionDto} from "../services/app/statistics-service";

export class Sponsors {
    private readonly sponsorService = resolve(SponsorService);

    contributions: SponsorContributionDto[] = [];
    isLoading = true;
    loadError = false;

    async attached() {
        this.isLoading = true;
        this.loadError = false;
        try {
            this.contributions = await this.sponsorService.getSponsorContributions();
        } catch {
            this.contributions = [];
            this.loadError = true;
        } finally {
            this.isLoading = false;
        }
    }

}

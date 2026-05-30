import {StatisticsService, DonationStatsDto} from "../../services/app/statistics-service";
import {resolve} from "@aurelia/kernel";
import {SponsorDto, SponsorsDto, SponsorService} from "../../services/app/sponsor-service";

export class Donations {
    private statisticsService = resolve(StatisticsService);
    private sponsorService = resolve(SponsorService);
    private accountInfo: DonationStatsDto;
    private sponsors: SponsorDto[];
    private activeSponsor: SponsorDto;
    private activeSponsorIndex: number = 0;
    private sponsorInterval: ReturnType<typeof setInterval> | null = null;


    attached() {
        this.loadLatestDonationInfo();
    }

    detaching() {
        if (this.sponsorInterval !== null) {
            clearInterval(this.sponsorInterval);
            this.sponsorInterval = null;
        }
    }

    async loadLatestDonationInfo() {
        this.accountInfo = await this.statisticsService.getDonationStats();
        this.sponsors = (await this.sponsorService.getSponsors()).sponsors;
        if (this.sponsors?.length) {
            this.activeSponsorIndex = Math.floor(Math.random() * this.sponsors.length);
            this.activeSponsor = this.sponsors[this.activeSponsorIndex];
            this.sponsorInterval = setInterval(() => this.rotateSponsor(), 60_000);
        }
    }

    private rotateSponsor() {
        if (!this.sponsors?.length) return;
        this.activeSponsorIndex = (this.activeSponsorIndex + 1) % this.sponsors.length;
        this.activeSponsor = this.sponsors[this.activeSponsorIndex];
    }
}

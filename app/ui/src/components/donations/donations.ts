import {StatisticsService, DonationStatsDto} from "../../services/app/statistics-service";
import {resolve} from "@aurelia/kernel";
import {SponsorContributionDto, SponsorDto, SponsorService} from "../../services/app/sponsor-service";
import {SponsorPaymentModal} from "./sponsor-payment-modal";
import {IRouter} from "@aurelia/router";

export class Donations {
    private statisticsService = resolve(StatisticsService);
    private sponsorService = resolve(SponsorService);
    private router = resolve(IRouter);
    private accountInfo: DonationStatsDto;
    private sponsors: SponsorDto[] = [];
    private contributions: SponsorContributionDto[] = [];
    private activeSponsor: SponsorDto;
    private activeSponsorIndex: number = 0;
    private sponsorInterval: ReturnType<typeof setInterval> | null = null;
    private sponsorPaymentModal: SponsorPaymentModal;

    get recentTransactions() {
        return this.accountInfo?.transactions?.slice(0, 3) ?? [];
    }

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
        this.contributions = await this.sponsorService.getSponsorContributions();
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

    showSponsorPaymentModal() {
        this.sponsorPaymentModal.showModal();
    }

    showSponsors() {
        this.router.load('/sponsors');
    }

    get totalSponsorTransactions() {
        return this.contributions?.length ?? 0;
    }
}

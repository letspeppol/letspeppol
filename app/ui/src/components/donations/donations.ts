import {StatisticsService, DonationStatsDto, SponsorContributionDto} from "../../services/app/statistics-service";
import {resolve} from "@aurelia/kernel";
import {SponsorDto, SponsorService} from "../../services/app/sponsor-service";
import {SponsorPaymentModal} from "./sponsor-payment-modal";
import {IRouter} from "@aurelia/router";

export class Donations {
    private statisticsService = resolve(StatisticsService);
    private sponsorService = resolve(SponsorService);
    private router = resolve(IRouter);
    private accountInfo: DonationStatsDto;
    private sponsors: SponsorDto[] = [];
    private activeSponsor: SponsorDto;
    private activeSponsorIndex: number = 0;
    private sponsorInterval: ReturnType<typeof setInterval> | null = null;
    private sponsorPaymentModal: SponsorPaymentModal;

    get recentTransactions() {
        const contributions = this.accountInfo?.contributions;
        if (contributions?.length) {
            return contributions.slice(0, 3);
        }
        return this.openCollectiveContributions.slice(0, 3);
    }

    private get openCollectiveContributions(): SponsorContributionDto[] {
        return this.accountInfo?.transactions
            ?.filter(transaction => transaction.amount?.value !== undefined)
            .map(transaction => ({
                name: transaction.fromAccount?.name || "OpenCollective supporter",
                message: "OpenCollective contribution",
                amount: transaction.amount.value,
                currency: transaction.amount.currency,
                date: transaction.createdAt
            })) ?? [];
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
        return this.accountInfo?.totalContributions ?? 0;
    }
}

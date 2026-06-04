import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {AppApi} from "./app-api";
import {CacheTtl} from "./cache-config";

export interface DonationStatsDto {
    totalContributions: number;
    totalProcessed: number;
    processedToday: number;
    maxProcessedLastWeek: number;
    invoicesRemaining: number;
    activeCompanies: number;
    transactions: Transaction[];
    contributions: SponsorContributionDto[];
}

export interface SponsorContributionDto {
    name: string;
    message: string;
    amount: number;
    currency: string;
    date: string;
}

export interface Transaction {
    type: string;
    fromAccount: FromAccount;
    amount: Amount;
    createdAt: string; // ISO datetime
}

export interface FromAccount {
    name: string;
    slug: string;
}

export interface Amount {
    value: number;      // BigDecimal → number (or string if you need precision)
    currency: string;
}

// export type ListItem = {
//     platformId: string;
//     docType: 'invoice' | 'credit-note';
//     direction: 'incoming' | 'outgoing';
//     counterPartyId: string;
//     counterPartyName: string;
//     createdAt: string; // ISO 8601 Date string
//     dueDate?: string; // ISO 8601 Date string
//     amount: number;
//     docId: string;
//     paymentTerms?: string;
//     paid?: string;
// };

export type DirectionTotals = {
    payableOpen: number;
    payableOverdue: number;
    payableThisYear: number;
    receivableOpen: number;
    receivableOverdue: number;
    receivableThisYear: number;
};

export type Totals = {
    inclVat: DirectionTotals;
    exclVat: DirectionTotals;
};

// export type DocumentQuery = {
//     userId?: string;
//     counterPartyId?: string;
//     counterPartyNameLike?: string;
//     docType?: 'invoice' | 'credit-note';
//     direction?: 'incoming' | 'outgoing';
//     docId?: string;
//     sortBy?: 'amountAsc' | 'amountDesc' | 'createdAtAsc' | 'createdAtDesc';
//     page?: number;
//     pageSize?: number;
// };

@singleton()
export class StatisticsService {
    private appApi = resolve(AppApi);
    private donationStatsCache?: { value: DonationStatsDto, expiresAt: number };
    private donationStatsRequest?: Promise<DonationStatsDto>;

    async getDonationStats() : Promise<DonationStatsDto> {
        if (this.donationStatsCache && this.donationStatsCache.expiresAt > Date.now()) {
            return this.donationStatsCache.value;
        }
        if (this.donationStatsRequest) {
            return this.donationStatsRequest;
        }
        this.donationStatsRequest = this.appApi.httpClient.get('/api/stats/donation')
            .then(response => response.json())
            .then((stats: DonationStatsDto) => {
                this.donationStatsCache = {value: stats, expiresAt: Date.now() + CacheTtl.donationStats};
                return stats;
            })
            .finally(() => this.donationStatsRequest = undefined);
        return this.donationStatsRequest;
    }

    clearDonationStatsCache() {
        this.donationStatsCache = undefined;
        this.donationStatsRequest = undefined;
    }

    clearCache() {
        this.clearDonationStatsCache();
    }

    async getTotals() : Promise<Totals> {
        return await this.appApi.httpClient.get(`/sapi/stats/account`).then(response => response.json());
    }

}

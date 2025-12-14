import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {AppApi} from "./app-api";

export interface DonationStatsDto {
    totalContributions: number;
    totalProcessed: number;
    processedToday: number;
    maxProcessedLastWeek: number;
    invoicesRemaining: number;
    transactions: Transaction[];
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

export type Totals = {
    totalPayableOpen: number;
    totalPayableOverdue: number;
    totalPayableThisYear: number;
    totalReceivableOpen: number;
    totalReceivableOverdue: number;
    totalReceivableThisYear: number;
}

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

    async getDonationStats() : Promise<DonationStatsDto> {
        return await this.appApi.httpClient.get('/api/stats/donation').then(response => response.json());
    }

    async getTotals() : Promise<Totals> {
        return await this.appApi.httpClient.get(`/sapi/stats/account`).then(response => response.json());
    }

}

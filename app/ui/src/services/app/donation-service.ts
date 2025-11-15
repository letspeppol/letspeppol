import {singleton} from "aurelia";
import {AppApi} from "./app-api";
import {resolve} from "@aurelia/kernel";

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


@singleton()
export class DonationService {
    private appApi = resolve(AppApi);

    async getDonationStats() : Promise<DonationStatsDto> {
        return await this.appApi.httpClient.get('/api/donation/stats').then(response => response.json());
    }
}
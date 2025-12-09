import {resolve} from "@aurelia/kernel";
import {ProxyApi} from "./proxy-api";

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

export class ProxyService {
    private letsPeppolApi = resolve(ProxyApi);

    async getTotals() : Promise<Totals> {
        return await this.letsPeppolApi.httpClient.get(`/v2/totals`).then(response => response.json());
    }

}

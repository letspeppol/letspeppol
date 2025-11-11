import {resolve} from "@aurelia/kernel";
import {ProxyApi} from "./proxy-api";

export type ListItem = {
    platformId: string;
    docType: 'invoice' | 'credit-note';
    direction: 'incoming' | 'outgoing';
    counterPartyId: string;
    counterPartyName: string;
    createdAt: string; // ISO 8601 Date string
    dueDate?: string; // ISO 8601 Date string
    amount: number;
    docId: string;
    paymentTerms?: string;
    paid?: string;
};

export type Totals = {
    totalPayable: number;
    totalReceivable: number;
}

export type DocumentQuery = {
    userId?: string;
    counterPartyId?: string;
    counterPartyNameLike?: string;
    docType?: 'invoice' | 'credit-note';
    direction?: 'incoming' | 'outgoing';
    docId?: string;
    sortBy?: 'amountAsc' | 'amountDesc' | 'createdAtAsc' | 'createdAtDesc';
    page?: number;
    pageSize?: number;
};


export class ProxyService {
    private letsPeppolApi = resolve(ProxyApi);

    async getDocuments(query: DocumentQuery = {}) : Promise<ListItem[]> {
        const defaults = {
            sortBy: 'createdAtAsc',
            page: 1,
            pageSize: 20,
        };

        const params = { ...defaults, ...query };

        const queryString = new URLSearchParams(
            Object.entries(params)
                .filter(([_, v]) => v)
                .map(([k, v]) => [k, String(v)])
        ).toString();

        const url = `/v2/documents?${queryString}`;

        return await this.letsPeppolApi.httpClient.get(url).then(response => response.json());
    }

    async getTotals() : Promise<Totals> {
        return await this.letsPeppolApi.httpClient.get(`/v2/totals`).then(response => response.json());
    }

    async markPaid(platformId: string, paid: string) {
        const body = {
            paid: paid
        };
        return await this.letsPeppolApi.httpClient.post(`/v2/documents/${platformId}`, JSON.stringify(body)).then(response => response.text());
    }

    async sendDocument(xml: string) {
        return await this.letsPeppolApi.httpClient.post('/v2/send', xml);
    }

    // Gets UBL
    async getDocument(platformId: string) {
        return await this.letsPeppolApi.httpClient.get(`/v2/documents/${platformId}`).then(response => response.text());
    }

}

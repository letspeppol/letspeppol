import {resolve} from "@aurelia/kernel";
import {ProxyApi} from "./proxy-api";
import sleep from "@web-eid/web-eid-library/utils/sleep";

export type ListItem = {
    platformId: string;
    docType: 'invoice' | 'credit-note';
    direction: 'incoming' | 'outgoing';
    counterPartyId: string;
    counterPartyName: string;
    createdAt: string; // ISO 8601 Date string
    amount: number;
    docId: string;
};

export class ProxyService {
    private letsPeppolApi = resolve(ProxyApi);

    async getDocuments(page: number, counterPartyNameLike: string = undefined) {
        let url = `/v2/documents?page=${page}&`;
        if (counterPartyNameLike) {
            url += `counterPartyNameLike=${counterPartyNameLike}`;
        }

        return await this.letsPeppolApi.httpClient.get(url).then(response => response.json());
    }

    async getIncomingInvoices(page: number) : Promise<ListItem[]> {
        return Promise.resolve([]);
        // return await this.letsPeppolApi.httpClient.get(`/v1/invoices/incoming?page=${page}&itemsPerPage=10`).then(response => response.json());
    }

    async getOutgoingInvoices(page: number): Promise<ListItem[]> {
        return Promise.resolve([]);
        // return await this.letsPeppolApi.httpClient.get(`/v1/invoices/outgoing?page=${page}&itemsPerPage=10`).then(response => response.json());
    }

    async sendDocument(xml: string) {
        return await this.letsPeppolApi.httpClient.post('/v2/send', xml);
    }

    async getDocument(docType: string, direction: string, uuid: string) {
        let type = 'invoices';
        if (docType === 'Credit-note') {
            type = 'credit-notes';
        }
        return await this.letsPeppolApi.httpClient.get(`/v1/${type}/${direction}/${uuid}`).then(response => response.text());
    }

}

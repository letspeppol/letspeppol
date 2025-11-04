import {resolve} from "@aurelia/kernel";
import {ProxyApi} from "./proxy-api";
import sleep from "@web-eid/web-eid-library/utils/sleep";

export type ListItemV1 = {
    uuid: string;
    type: 'Invoice' | 'CreditNote' | string;
    direction: 'incoming' | 'outgoing';
    format: string;
    number: string;
    senderId: string;
    senderName?: string;
    recipientId: string;
    recipientName?: string;
    requestSentAt?: string;
    responseSentAt?: string;
    success: boolean;
    errorCode: string | null;
}

export class ProxyService {
    private letsPeppolApi = resolve(ProxyApi);

    async getIncomingInvoices(page: number) : Promise<ListItemV1[]> {
        return Promise.resolve([]);
        // return await this.letsPeppolApi.httpClient.get(`/v1/invoices/incoming?page=${page}&itemsPerPage=10`).then(response => response.json());
    }

    async getOutgoingInvoices(page: number): Promise<ListItemV1[]> {
        return Promise.resolve([]);
        // return await this.letsPeppolApi.httpClient.get(`/v1/invoices/outgoing?page=${page}&itemsPerPage=10`).then(response => response.json());
    }

    async sendDocument(xml: string) {
        await sleep(1000);
        return Promise.resolve();
        // return await this.letsPeppolApi.httpClient.post('/v1/send', xml);
    }

    async getDocument(docType: string, direction: string, uuid: string) {
        let type = 'invoices';
        if (docType === 'Credit-note') {
            type = 'credit-notes';
        }
        return await this.letsPeppolApi.httpClient.get(`/v1/${type}/${direction}/${uuid}`).then(response => response.text());
    }

}

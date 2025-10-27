import {resolve} from "@aurelia/kernel";
import {ProxyApi} from "./api/proxy-api";

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

    async getIncomingInvoices() : Promise<ListItemV1[]> {
        return await this.letsPeppolApi.httpClient.get('/v1/invoices/incoming').then(response => response.json());
    }

    async getOutgoingInvoices(): Promise<ListItemV1[]> {
        return await this.letsPeppolApi.httpClient.get('/v1/invoices/outgoing').then(response => response.json());
    }

    async sendDocument(xml: string) {
        return await this.letsPeppolApi.httpClient.post('/v1/send', xml);
    }

    async getDocument(docType: string, direction: string, uuid: string) {
        return await this.letsPeppolApi.httpClient.get(`/v1/${docType}/${direction}/${uuid}`).then(response => response.json());
    }

}
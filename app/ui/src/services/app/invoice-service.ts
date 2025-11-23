import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {AppApi} from "./app-api";

export interface InvoiceDraftDto {
    id?: number,
    docType: string,
    docId: string,
    counterPartyName?: string,
    createdAt?: string,
    dueDate?: string,
    amount: number,
    xml: string
}

export enum DocumentDirection {
  INCOMING = "INCOMING",
  OUTGOING = "OUTGOING",
}

export enum DocumentType {
  INVOICE = "INVOICE",
  CREDIT_NOTE = "CREDIT_NOTE",
}

// ISO 4217 code like "EUR", "USD"
export type CurrencyCode = string;

export interface DocumentDto {
    id?: string;                    // UUID
    direction: DocumentDirection;
    ownerPeppolId: string;
    partnerPeppolId: string;
    proxyOn?: string;               // ISO datetime (Instant)
    scheduledOn?: string;           // ISO datetime (Instant)
    processedOn?: string;           // ISO datetime (Instant)
    processedStatus?: string;
    ubl: string;
    draftedOn?: string;             // ISO datetime (Instant)
    readOn?: string;                // ISO datetime (Instant)
    paidOn?: string;                // ISO datetime (Instant)
    partnerName?: string;
    invoiceReference?: string;
    type?: DocumentType;
    currency?: CurrencyCode;
    amount?: number;                // BigDecimal → number (or string if you need precision)
    issueDate?: string;             // ISO datetime (Instant)
    dueDate?: string;               // ISO datetime (Instant)
    paymentTerms?: string;
}

export interface ValidationResultDto {
    isValid: boolean,
    errorCount: number,
    errors: ValidationErrorDto[],
    vesId: string,
    detectedVESIDRaw: string,
}

export interface ValidationErrorDto {
    column: number,
    line: number,
    message: string,
    severity: string,
}

@singleton()
export class InvoiceService {
    private appApi = resolve(AppApi);

    async validate(xml: string) : Promise<ValidationResultDto> {
        return await this.appApi.httpClient.post(`/api/document/validate`, xml).then(response => response.json());
    }

    // Drafts

    async getInvoiceDrafts() : Promise<InvoiceDraftDto[]> {
        return await this.appApi.httpClient.get('/api/invoice/draft').then(response => response.json());
    }

    async createInvoiceDraft(draft: InvoiceDraftDto) : Promise<InvoiceDraftDto> {
        return await this.appApi.httpClient.post('/api/invoice/draft', JSON.stringify(draft)).then(response => response.json());
    }

    async updateInvoiceDraft(id: number, draft: InvoiceDraftDto) : Promise<InvoiceDraftDto> {
        return await this.appApi.httpClient.put(`/api/invoice/draft/${id}`, JSON.stringify(draft)).then(response => response.json());
    }

    async getDocuments() : Promise<DocumentDto[]> {
        return await this.appApi.httpClient.get('/api/document').then(response => response.json());
    }

    async getDocument(id: string) : Promise<DocumentDto> {
        return await this.appApi.httpClient.get(`/api/document/${id}`).then(response => response.json());
    }

    async createDocument(xml: string) : Promise<DocumentDto> {
        return await this.appApi.httpClient.post('/api/document', xml).then(response => response.json());
    }

    async saveDocument(id: string, xml: string) : Promise<DocumentDto> {
        return await this.appApi.httpClient.put(`/api/document/${id}`, xml).then(response => response.json());
    }

    async sendDocument(id: string) : Promise<DocumentDto> {
        return await this.appApi.httpClient.put(`/api/document/${id}/send`).then(response => response.json());
    }

    async markReadDocument(id: string) : Promise<DocumentDto> {
        return await this.appApi.httpClient.put(`/api/document/${id}/read`).then(response => response.json());
    }

    async markPaidDocument(id: string) : Promise<DocumentDto> {
        return await this.appApi.httpClient.put(`/api/document/${id}/paid`).then(response => response.json());
    }

    async deleteDocument(id: string) {
        return await this.appApi.httpClient.delete(`/api/document/${id}`);
    }

    async deleteInvoiceDraft(id: number) {
        return await this.appApi.httpClient.delete(`/api/invoice/draft/${id}`);
    }
}

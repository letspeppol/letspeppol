import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {AppApi} from "./app-api";

export interface DocumentPageDto {
    content: DocumentDto[];
    page: number,
    size: number,
    totalElements: number,
    totalPages: number,
    last: boolean
}

export enum DocumentDirection {
  INCOMING = "INCOMING",
  OUTGOING = "OUTGOING",
}

export enum DocumentType {
  INVOICE = "INVOICE",
  CREDIT_NOTE = "CREDIT_NOTE",
}

export interface DocumentQuery {
    type?: DocumentType;
    direction?: DocumentDirection;
    partnerName?: string;
    invoiceReference?: string;
    paid?: boolean;
    read?: boolean;
    draft?: boolean;
    pageable: Pageable;
}

type SortDirection = "asc" | "desc";

interface SortOrder {
    property: string;
    direction: SortDirection;
}

interface Pageable {
    page: number;
    size: number;
    sort?: SortOrder[];
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
        return await this.appApi.httpClient.post(`/sapi/document/validate`, xml).then(response => response.json());
    }

    async getDocuments(query: DocumentQuery) : Promise<DocumentPageDto> {
        const search = new URLSearchParams();

        if (query.type) search.append("type", query.type);
        if (query.direction) search.append("direction", query.direction);
        if (query.partnerName) search.append("partnerName", query.partnerName);
        if (query.invoiceReference) search.append("invoiceReference", query.invoiceReference);
        if (query.paid !== undefined) search.append("paid", String(query.paid));
        if (query.read !== undefined) search.append("read", String(query.read));
        if (query.draft !== undefined) search.append("draft", String(query.draft));

        // pageable
        search.append("page", String(query.pageable.page));
        search.append("size", String(query.pageable.size));
        if (query.pageable.sort && query.pageable.sort.length > 0) {
            for (const s of query.pageable.sort) {
                search.append("sort", `${s.property},${s.direction}`);
            }
        }
        return await this.appApi.httpClient.get(`/sapi/document?${search.toString()}`).then(response => response.json());
    }

    async getDocument(id: string) : Promise<DocumentDto> {
        return await this.appApi.httpClient.get(`/sapi/document/${id}`).then(response => response.json());
    }

    async createDocument(xml: string, draft: boolean = false) : Promise<DocumentDto> {
        return await this.appApi.httpClient.post(`/sapi/document?draft=${draft}`, xml).then(response => response.json());
    }

    async updateDocument(id: string, xml: string, draft: boolean = false) : Promise<DocumentDto> {
        return await this.appApi.httpClient.put(`/sapi/document/${id}?draft=${draft}`, xml).then(response => response.json());
    }

    async sendDocument(id: string) : Promise<DocumentDto> {
        return await this.appApi.httpClient.put(`/sapi/document/${id}/send`).then(response => response.json());
    }

    async markReadDocument(id: string) : Promise<DocumentDto> {
        return await this.appApi.httpClient.put(`/sapi/document/${id}/read`).then(response => response.json());
    }

    async togglePaidDocument(id: string) : Promise<DocumentDto> {
        return await this.appApi.httpClient.put(`/sapi/document/${id}/paid`).then(response => response.json());
    }

    async deleteDocument(id: string) {
        return await this.appApi.httpClient.delete(`/sapi/document/${id}`);
    }

    async deleteInvoiceDraft(id: number) {
        return await this.appApi.httpClient.delete(`/sapi/invoice/draft/${id}`);
    }

    async downloadPdf(id: string) {
        return await this.appApi.httpClient.get(`/sapi/document/${id}/pdf`);

    }
}

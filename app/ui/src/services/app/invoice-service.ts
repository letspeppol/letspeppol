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

export interface ValidationResultDto {
    isValid: boolean,
    errorCount: number,
    errors: ValidationErrorDto[]
    detectedVESIDRaw: string
}

export interface ValidationErrorDto {
    column: number,
    line: number,
    message: string,
    severity: string
}

@singleton()
export class InvoiceService {
    private appApi = resolve(AppApi);

    async validate(xml: string) : Promise<ValidationResultDto> {
        return await this.appApi.httpClient.post(`/api/invoice/validate`, JSON.stringify({xml: xml})).then(response => response.json());
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

    async deleteInvoiceDraft(id:number) {
        return await this.appApi.httpClient.delete(`/api/invoice/draft/${id}`);
    }
}

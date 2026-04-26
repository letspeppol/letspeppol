import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {AppApi} from "./app-api";
import type {DocumentDto} from "./invoice-service";

export interface LinkCustomerDto {
    customerPeppolId: string;
    customerEmail: string;
    customerName: string;
}

export interface CustomerDto {
    customerPeppolId: string;
    customerEmail: string;
    customerName: string;
    invitedOn?: string;
    verifiedOn?: string;
    lastDownloadCreatedOn?: string;
    lastDownloadIssuedOn?: string;
}

export interface DocumentPageDto {
    content: DocumentDto[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    last: boolean;
}

type SortDirection = "asc" | "desc";

export interface SortOrder {
    property: string;
    direction: SortDirection;
}

export interface Pageable {
    page: number;
    size: number;
    sort?: SortOrder[];
}

@singleton()
export class AccountantService {
    private appApi = resolve(AppApi);

    async linkCustomer(dto: LinkCustomerDto): Promise<void> {
        await this.appApi.httpClient.post(`/sapi/accountant/link-customer`, JSON.stringify(dto));
    }

    async confirmCustomerLink(token: string): Promise<void> {
        const qs = new URLSearchParams();
        qs.set("token", token);
        await this.appApi.httpClient.post(`/sapi/accountant/confirm-customer-link?${qs.toString()}`);
    }

    async getCustomersForAccountant(): Promise<CustomerDto[]> {
        return await this.appApi.httpClient.get(`/sapi/accountant/customers`).then(r => r.json());
    }

    async getCustomerDocuments(params: { customerPeppolId: string; pageable: Pageable }): Promise<DocumentPageDto> {
        const qs = new URLSearchParams();
        qs.set("customerPeppolId", params.customerPeppolId);

        // pageable
        qs.set("page", String(params.pageable.page));
        qs.set("size", String(params.pageable.size));
        if (params.pageable.sort && params.pageable.sort.length > 0) {
            for (const s of params.pageable.sort) {
                qs.append("sort", `${s.property},${s.direction}`);
            }
        }

        return await this.appApi.httpClient.get(`/sapi/accountant/documents?${qs.toString()}`).then(r => r.json());
    }
}


import {resolve} from "@aurelia/kernel";
import {singleton} from "aurelia";
import {KYCApi} from "./kyc-api";

export type ReviewStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface RegistrationReviewDto {
    id: number;
    createdOn: string;
    accountEmail: string;
    accountVerified: boolean;
    peppolId: string;
    companyName: string;
    companySuspended: boolean;
    companyHasAdmin: boolean;
    directorName: string;
    signerName: string;
    requestedType: string | null;
    reviewStatus: ReviewStatus;
    reviewedBy?: string;
    reviewedOn?: string;
}

export interface RegistrationReviewDecisionResponse {
    review: RegistrationReviewDto;
    registration?: {
        peppolActive: boolean;
        errorCode?: string;
        body?: string;
    };
}

@singleton()
export class RegistrationReviewService {
    private readonly kycApi = resolve(KYCApi);

    async getReviews(status: ReviewStatus): Promise<RegistrationReviewDto[]> {
        return this.kycApi.httpClient.get(`/sapi/backoffice/registration-reviews?status=${status}`).then(response => response.json());
    }

    async downloadContract(id: number): Promise<Blob> {
        return this.kycApi.httpClient.get(`/sapi/backoffice/registration-reviews/${id}/contract`).then(response => response.blob());
    }

    async approve(id: number): Promise<RegistrationReviewDecisionResponse> {
        return this.kycApi.httpClient.post(`/sapi/backoffice/registration-reviews/${id}/approve`).then(response => response.json());
    }

    async reject(id: number): Promise<RegistrationReviewDecisionResponse> {
        return this.kycApi.httpClient.post(`/sapi/backoffice/registration-reviews/${id}/reject`).then(response => response.json());
    }
}

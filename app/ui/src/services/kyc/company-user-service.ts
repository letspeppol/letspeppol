import {resolve} from "@aurelia/kernel";
import {singleton} from "aurelia";
import {KYCApi} from "./kyc-api";

export type CompanyUserStatus = 'ACTIVE' | 'INVITED' | 'SUSPENDED';

export interface CompanyUserDto {
    id: number;
    name: string;
    email: string;
    type: string;
    status: CompanyUserStatus;
    permissionMask: number;
    createdOn: string;
    lastUsed: string;
    invitationExpiresOn: string | null;
}

export interface InviteUserRequest {
    email: string;
    name: string;
    permissionMask: number;
}

export interface InvitationInfo {
    email: string;
    name: string;
    companyName: string;
    peppolId: string;
    passwordRequired: boolean;
}

export interface AcceptInvitationRequest {
    token: string;
    newPassword?: string;
}

@singleton()
export class CompanyUserService {
    private readonly kycApi = resolve(KYCApi);

    async getUsers(): Promise<CompanyUserDto[]> {
        const response = await this.kycApi.httpClient.get('/sapi/users');
        return response.json();
    }

    async inviteUser(request: InviteUserRequest): Promise<CompanyUserDto> {
        const response = await this.kycApi.httpClient.post('/sapi/users', JSON.stringify(request));
        return response.json();
    }

    async updatePermissions(id: number, permissionMask: number): Promise<CompanyUserDto> {
        const response = await this.kycApi.httpClient.put(`/sapi/users/${id}/permissions`, JSON.stringify({permissionMask}));
        return response.json();
    }

    async suspendUser(id: number): Promise<CompanyUserDto> {
        const response = await this.kycApi.httpClient.post(`/sapi/users/${id}/suspend`);
        return response.json();
    }

    async reactivateUser(id: number): Promise<CompanyUserDto> {
        const response = await this.kycApi.httpClient.post(`/sapi/users/${id}/reactivate`);
        return response.json();
    }

    async resendInvitation(id: number): Promise<void> {
        await this.kycApi.httpClient.post(`/sapi/users/${id}/resend`);
    }

    async removeUser(id: number): Promise<void> {
        await this.kycApi.httpClient.delete(`/sapi/users/${id}`);
    }

    async verifyInvitation(token: string): Promise<InvitationInfo> {
        const response = await this.kycApi.httpClient.post(`/api/invitation/verify?token=${encodeURIComponent(token)}`);
        return response.json();
    }

    async acceptInvitation(request: AcceptInvitationRequest): Promise<void> {
        await this.kycApi.httpClient.post('/api/invitation/accept', JSON.stringify(request));
    }
}

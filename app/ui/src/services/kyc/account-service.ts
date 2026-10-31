import {resolve} from '@aurelia/kernel';
import {KYCApi} from './kyc-api';

export interface CurrentAccount {
    name: string;
    email: string;
}

export class AccountService {
    private readonly kycApi = resolve(KYCApi);

    async getCurrentAccount(): Promise<CurrentAccount> {
        const response = await this.kycApi.httpClient.get('/sapi/account');
        return response.json();
    }
}

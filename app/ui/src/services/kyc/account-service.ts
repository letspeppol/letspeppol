import {resolve} from '@aurelia/kernel';
import {KYCApi} from './kyc-api';

export interface CurrentAccount {
    name: string;
    email: string;
}

export class AccountService {
    private readonly kycApi = resolve(KYCApi);

    async getCurrentAccount(): Promise<CurrentAccount> {
        const response = await this.kycApi.httpClient.get('/sapi/company/account');
        // This endpoint returns the signed-in account under the legacy director field names.
        const account = await response.json();
        return {name: account.directorName, email: account.directorEmail};
    }
}

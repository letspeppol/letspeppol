import {resolve} from "@aurelia/kernel";
import {KYCApi} from "./kyc-api";
import {KycCompanyResponse} from "./registration-service";

export interface CompanySearchParams {
    vatNumber?: string;
    peppolId?: string;
    companyName?: string;
}

export class CompanySearchService {
    public kycApi = resolve(KYCApi);

    async searchCompany(params: CompanySearchParams): Promise<KycCompanyResponse[]> {
        const qs = new URLSearchParams();

        if (params.vatNumber?.trim()) qs.set(`vatNumber`, params.vatNumber.trim());
        if (params.peppolId?.trim()) qs.set(`peppolId`, params.peppolId.trim());
        if (params.companyName?.trim()) qs.set(`companyName`, params.companyName.trim());

        const url = qs.toString() ? `/sapi/company/search?${qs.toString()}` : `/sapi/company/search`;
        const response = await this.kycApi.httpClient.get(url);

        return response.json();
    }

}

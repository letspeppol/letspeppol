import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";
import {CompanyService} from "../../../../services/app/company-service";
import {isVatExemptRuleset} from "../../../../services/app/vat-rules";

export class TotalInfo {
    private invoiceContext = resolve(InvoiceContext);
    private companyService = resolve(CompanyService);

    isFixedVatMode(): boolean {
        return !this.companyService.myCompany?.vatNumber?.trim() || isVatExemptRuleset(this.companyService.myCompany?.vatRuleset);
    }
}

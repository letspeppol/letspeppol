import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";
import {CompanyService} from "../../../../services/app/company-service";
import {shouldUseFixedVatMode} from "../../../../services/app/vat-rules";

export class TotalInfo {
    private invoiceContext = resolve(InvoiceContext);
    private companyService = resolve(CompanyService);

    isFixedVatMode(): boolean {
        return shouldUseFixedVatMode(
            this.companyService.myCompany?.vatNumber,
            this.companyService.myCompany?.vatRuleset,
            this.invoiceContext.readOnly,
            this.invoiceContext.selectedDocument?.direction,
        );
    }
}

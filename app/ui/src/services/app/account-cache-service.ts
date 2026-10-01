import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../invoice/invoice-context";
import {PartnerContext} from "../../partner/partner-context";
import {ProductContext} from "../../product/product-context";
import {PartnerService} from "./partner-service";

@singleton()
export class AccountCacheService {
    private readonly invoiceContext = resolve(InvoiceContext);
    private readonly partnerContext = resolve(PartnerContext);
    private readonly productContext = resolve(ProductContext);
    private readonly partnerService = resolve(PartnerService);

    clearAll() {
        this.invoiceContext.clearAccountCache();
        this.partnerContext.clearAccountCache();
        this.productContext.clearAccountCache();
        this.partnerService.clearCache();
    }
}

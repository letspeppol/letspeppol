import {bindable, computed} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../../invoice-context";
import {Party} from "../../../../services/peppol/ubl";

export class CustomerInfo {
    private invoiceContext = resolve(InvoiceContext);
    @bindable party: Party;
    @bindable readOnly: boolean;
    @bindable showCustomerModal;

    @computed('invoiceContext.selectedInvoice.AccountingCustomerParty.Party.PartyName.Name')
    get isCustomerInfoComplete(): boolean {
        return !!this.invoiceContext.selectedInvoice?.AccountingCustomerParty?.Party?.PartyName?.Name;
    }

}

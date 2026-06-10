import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../invoice-context";
import {ClassifiedTaxCategory, CreditNoteLine, getAmount, InvoiceLine, UBLLine} from "../../../services/peppol/ubl";
import {InvoiceCalculator, roundTwoDecimals} from "../../invoice-calculator";
import {DocumentType} from "../../../services/app/invoice-service";
import {bindable, IEventAggregator} from "aurelia";
import {ProductDto} from "../../../services/app/product-service";
import {CompanyService} from "../../../services/app/company-service";
import {
    createZeroVatCategory,
    getVatRulesetExemptionReason,
    getVatRulesetLabelKey,
    getVatRulesetLabel,
    isVatExemptRuleset,
    VatRuleset,
} from "../../../services/app/vat-rules";
import {AlertType} from "../../../components/alert/alert";
import {I18N} from "@aurelia/i18n";
import {InvoiceZeroVatReasonModal} from "./modals/invoice-zero-vat-reason-modal";

export class InvoiceEditItems {
    private invoiceContext = resolve(InvoiceContext);
    private invoiceCalculator = resolve(InvoiceCalculator);
    private companyService = resolve(CompanyService);
    private ea: IEventAggregator = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);

    @bindable readOnly;
    @bindable autoSave;

    taxCategories: ClassifiedTaxCategory[] = [
        { ID: "S", Percent: 21, TaxScheme: { ID: 'VAT' } },
        { ID: "S", Percent: 12, TaxScheme: { ID: 'VAT' } },
        { ID: "S", Percent: 6, TaxScheme: { ID: 'VAT' } },
        { ID: "Z", Percent: 0, TaxScheme: { ID: 'VAT' } },
    ];
    vatRateOptions = [21, 12, 6, 0];
    zeroVatReasonModal: InvoiceZeroVatReasonModal;

    getVatRulesetLabelKey(vatRuleset: VatRuleset) {
        return getVatRulesetLabelKey(vatRuleset);
    }

    recalculateLinePositions() {
        for (let i = 0; i < this.invoiceContext.lines.length; i++) {
            this.invoiceContext.lines[i].ID = (i + 1).toString();
        }
    }

    calcLineTotal(line: UBLLine) {
        const quantity = getAmount(line);
        line.LineExtensionAmount.value = roundTwoDecimals(line.Price.PriceAmount.value * quantity.value);
        this.invoiceCalculator.calculateTaxAndTotals(this.invoiceContext.selectedInvoice);
        this.checkLineAutoSave(line);
    }

    deleteLine(line: UBLLine) {
        this.invoiceContext.lines.splice(this.invoiceContext.lines.findIndex(item => item === line), 1);
        this.invoiceCalculator.calculateTaxAndTotals(this.invoiceContext.selectedInvoice);
        this.autoSave();
    }

    nameOnChange(line: UBLLine) {
        this.checkLineAutoSave(line);
    }

    selectProduct(p: ProductDto, line: UBLLine) {
        if (p.costPrice) {
            line.Price.PriceAmount.value = p.costPrice;
        }
        if (p.description) {
            line.Item.Description = p.description;
        }
        if (p.taxPercentage != null) {
            const taxCategory = this.taxCategories.find(item => item.Percent === p.taxPercentage);
            if (taxCategory) {
                line.Item.ClassifiedTaxCategory = taxCategory;
            }
        }
    }

    isAccountVatExempt(): boolean {
        return isVatExemptRuleset(this.companyService.myCompany?.vatRuleset);
    }

    vatRateChanged(line: UBLLine, percent: number) {
        if (this.isAccountVatExempt()) {
            line.Item.ClassifiedTaxCategory = {
                ID: 'E',
                Percent: 0,
                TaxExemptionReason: getVatRulesetExemptionReason(this.companyService.myCompany?.vatRuleset, key => this.i18n.tr(key)),
                TaxScheme: { ID: 'VAT' }
            };
            this.calcLineTotal(line);
            return;
        }

        if (percent === 0) {
            line.Item.ClassifiedTaxCategory = {
                ID: '',
                Percent: 0,
                TaxExemptionReasonCode: undefined,
                TaxScheme: { ID: 'VAT' },
                TaxExemptionReason: line.Item.ClassifiedTaxCategory?.TaxExemptionReason
            };
            this.calcLineTotal(line);
            return;
        } else {
            line.Item.ClassifiedTaxCategory = {
                ID: 'S',
                Percent: percent,
                TaxScheme: { ID: 'VAT' }
            };
        }
        this.calcLineTotal(line);
    }

    getVatReadonlyTitle(): string {
        const reason = getVatRulesetLabel(this.companyService.myCompany?.vatRuleset, key => this.i18n.tr(key));
        return `${reason} - changeable in Account info`;
    }

    checkLineAutoSave(line: InvoiceLine | CreditNoteLine) {
        let autosave = false;
        if (line?.Item?.Name && line?.Price?.PriceAmount?.value) {
            if (this.invoiceContext.selectedDocumentType === DocumentType.INVOICE && (line as InvoiceLine).InvoicedQuantity?.value
                || this.invoiceContext.selectedDocumentType === DocumentType.CREDIT_NOTE && (line as CreditNoteLine).CreditedQuantity?.value) {
                autosave = true;
            }
        }
        if (autosave) {
            this.autoSave();
        }
    }
}

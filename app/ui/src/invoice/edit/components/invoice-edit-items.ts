import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../invoice-context";
import {ClassifiedTaxCategory, CreditNoteLine, getAmount, InvoiceLine, normalizeLinePrice, UBLLine} from "../../../services/peppol/ubl";
import {InvoiceCalculator, roundTwoDecimals} from "../../invoice-calculator";
import {DocumentType} from "../../../services/app/invoice-service";
import {bindable} from "aurelia";
import {ProductDto} from "../../../services/app/product-service";
import {CompanyService} from "../../../services/app/company-service";
import {
    createNotSubjectToVatCategory,
    createVatExemptCategory,
    isVatExemptRuleset,
} from "../../../services/app/vat-rules";
import {InvoiceZeroVatReasonModal} from "./modals/invoice-zero-vat-reason-modal";
import {I18N} from "@aurelia/i18n";

export class InvoiceEditItems {
    private invoiceContext = resolve(InvoiceContext);
    private invoiceCalculator = resolve(InvoiceCalculator);
    private companyService = resolve(CompanyService);
    private i18n = resolve(I18N);

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

    private hasNoVatNumber(): boolean {
        return !this.companyService.myCompany?.vatNumber?.trim();
    }

    isFixedVatMode(): boolean {
        return this.hasNoVatNumber() || this.isAccountVatExempt();
    }

    recalculateLinePositions() {
        for (let i = 0; i < this.invoiceContext.lines.length; i++) {
            this.invoiceContext.lines[i].ID = (i + 1).toString();
        }
    }

    calcLineTotal(line: UBLLine) {
        const quantity = getAmount(line);
        const unitPrice = normalizeLinePrice(line);
        line.LineExtensionAmount.value = roundTwoDecimals(unitPrice * quantity.value);
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
            if (this.hasNoVatNumber()) {
                line.Item.ClassifiedTaxCategory = createNotSubjectToVatCategory();
                this.calcLineTotal(line);
                return;
            }
            if (this.isAccountVatExempt()) {
                line.Item.ClassifiedTaxCategory = createVatExemptCategory(this.i18n.tr('account.vat-ruleset.options.VAT_EXEMPT_ART_56BIS'));
                this.calcLineTotal(line);
                return;
            }
            const taxCategory = this.taxCategories.find(item => item.Percent === p.taxPercentage);
            if (taxCategory) {
                line.Item.ClassifiedTaxCategory = taxCategory.Percent === 0
                    ? { ID: '', Percent: 0, TaxScheme: { ID: 'VAT' } }
                    : taxCategory;
                this.calcLineTotal(line);
            }
        }
    }

    isAccountVatExempt(): boolean {
        return isVatExemptRuleset(this.companyService.myCompany?.vatRuleset);
    }

    vatRateChanged(line: UBLLine, percent: number) {
        if (this.hasNoVatNumber()) {
            line.Item.ClassifiedTaxCategory = createNotSubjectToVatCategory();
            this.calcLineTotal(line);
            return;
        }
        if (this.isAccountVatExempt()) {
            line.Item.ClassifiedTaxCategory = createVatExemptCategory(this.i18n.tr('account.vat-ruleset.options.VAT_EXEMPT_ART_56BIS'));
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

    checkLineAutoSave(line: InvoiceLine | CreditNoteLine) {
        let autosave = false;
        if (line?.Item?.Name && line?.Price?.PriceAmount?.value != null) {
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

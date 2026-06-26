import {resolve} from "@aurelia/kernel";
import {InvoiceContext} from "../../invoice-context";
import {ClassifiedTaxCategory, CreditNoteLine, getAmount, InvoiceLine, normalizeLinePrice, UBLLine} from "../../../services/peppol/ubl";
import {InvoiceCalculator, roundTwoDecimals} from "../../invoice-calculator";
import {DocumentType, InvoiceService} from "../../../services/app/invoice-service";
import {bindable} from "aurelia";
import {ProductDto} from "../../../services/app/product-service";
import {CompanyService} from "../../../services/app/company-service";
import {
    applySharedVatReasonText,
    createNotSubjectToVatCategory,
    createVatExemptCategory,
    getDisplayedVatRatePercent,
    getReadonlyDisplayedVatRatePercent,
    getSharedVatReasonText,
    isVatExemptRuleset,
    ZeroVatReasonId,
} from "../../../services/app/vat-rules";
import {InvoiceZeroVatReasonModal} from "./modals/invoice-zero-vat-reason-modal";
import {I18N} from "@aurelia/i18n";

export class InvoiceEditItems {
    private invoiceContext = resolve(InvoiceContext);
    private invoiceCalculator = resolve(InvoiceCalculator);
    private invoiceService = resolve(InvoiceService);
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

    getDisplayedVatRate(line: UBLLine): number | undefined {
        return getDisplayedVatRatePercent(line?.Item?.ClassifiedTaxCategory);
    }

    getReadonlyDisplayedVatRate(line: UBLLine): number {
        return getReadonlyDisplayedVatRatePercent(line?.Item?.ClassifiedTaxCategory);
    }

    getVatSelectionTooltip(
        line: UBLLine,
        reasonId?: string,
        reasonText?: string,
    ): string | undefined {
        const taxCategory = line?.Item?.ClassifiedTaxCategory;
        const effectiveReasonId = reasonId?.trim() || taxCategory?.ID;
        const effectiveReasonText = reasonText?.trim() || taxCategory?.TaxExemptionReason?.trim();

        if (!taxCategory || effectiveReasonId == 'S') {
            return undefined;
        }
        const reasonType = this.getVatReasonTypeLabel(effectiveReasonId);

        if (reasonType && effectiveReasonText) {
            return `${this.i18n.tr('invoice.zero-vat-reason.modal.reason-type')}: ${reasonType}\n${this.i18n.tr('invoice.zero-vat-reason.modal.reason-text')}: ${effectiveReasonText}`;
        }
        if (reasonType) {
            return `${this.i18n.tr('invoice.zero-vat-reason.modal.reason-type')}: ${reasonType}`;
        }
        if (effectiveReasonText) {
            return `${this.i18n.tr('invoice.zero-vat-reason.modal.reason-text')}: ${effectiveReasonText}`;
        }
        return undefined;
    }

    private getVatReasonTypeLabel(reasonId: string | undefined): string | undefined {
        const trimmedReasonId = reasonId?.trim();
        if (!trimmedReasonId) {
            return undefined;
        }

        const primaryKey = `invoice.zero-vat-reason.options.${trimmedReasonId}`;
        const primaryTranslation = this.i18n.tr(primaryKey);
        if (primaryTranslation !== primaryKey) {
            return primaryTranslation;
        }

        const legacyKey = `alert.invoice.zero-vat-reason.options.${trimmedReasonId}`;
        const legacyTranslation = this.i18n.tr(legacyKey);
        if (legacyTranslation !== legacyKey) {
            return legacyTranslation;
        }

        return trimmedReasonId;
    }

    hasVatNumber(): boolean {
        return !!this.companyService.myCompany?.vatNumber?.trim();
    }

    lineTotalAmount(lineTotal: number, taxCategory: ClassifiedTaxCategory | undefined): number {
        if (this.hasVatNumber()) {
            return lineTotal;
        }

        const vatRate = getDisplayedVatRatePercent(taxCategory) ?? 0;
        return roundTwoDecimals(lineTotal * (1 + (vatRate / 100)));
    }

    recalculateLinePositions() {
        for (let i = 0; i < this.invoiceContext.lines.length; i++) {
            this.invoiceContext.lines[i].ID = (i + 1).toString();
        }
    }

    calcLineTotal(line: UBLLine, autoSave: boolean = true) {
        const quantity = getAmount(line);
        const unitPrice = normalizeLinePrice(line);
        line.LineExtensionAmount.value = roundTwoDecimals(unitPrice * quantity.value);
        this.invoiceCalculator.calculateTaxAndTotals(this.invoiceContext.selectedInvoice);
        if (autoSave) {
            this.checkLineAutoSave(line);
        }
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
            if (!this.hasVatNumber()) {
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
                if (taxCategory.Percent === 0) {
                    this.startZeroVatReasonSelection(line);
                } else {
                    line.Item.ClassifiedTaxCategory = taxCategory;
                    this.calcLineTotal(line);
                }
            }
        }
    }

    isAccountVatExempt(): boolean {
        return isVatExemptRuleset(this.companyService.myCompany?.vatRuleset);
    }

    isVatRateEditable(): boolean {
        return !this.readOnly && !this.isAccountVatExempt();
    }

    canEditZeroVatReason(line: UBLLine): boolean {
        return !this.readOnly
            && !this.isAccountVatExempt()
            && line.Item.ClassifiedTaxCategory?.Percent === 0;
    }

    vatRateChanged(line: UBLLine, percent: number) {
        if (!this.hasVatNumber()) {
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
            this.startZeroVatReasonSelection(line);
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
        const hasPersistableTaxCategory = !!line?.Item?.ClassifiedTaxCategory?.ID?.trim();
        if (line?.Item?.Name && line?.Price?.PriceAmount?.value != null && hasPersistableTaxCategory) {
            if (this.invoiceContext.selectedDocumentType === DocumentType.INVOICE && (line as InvoiceLine).InvoicedQuantity?.value
                || this.invoiceContext.selectedDocumentType === DocumentType.CREDIT_NOTE && (line as CreditNoteLine).CreditedQuantity?.value) {
                autosave = true;
            }
        }
        if (autosave) {
            this.autoSave();
        }
    }

    private startZeroVatReasonSelection(line: UBLLine) {
        const previousTaxCategory = line.Item.ClassifiedTaxCategory
            ? structuredClone(line.Item.ClassifiedTaxCategory)
            : undefined;

        line.Item.ClassifiedTaxCategory = {
            ID: '',
            Percent: 0,
            TaxExemptionReasonCode: undefined,
            TaxScheme: { ID: 'VAT' },
            TaxExemptionReason: previousTaxCategory?.TaxExemptionReason
        };
        this.calcLineTotal(line, false);
        this.zeroVatReasonModal.showModal(line, line.Item.ClassifiedTaxCategory, previousTaxCategory);
    }

    recordVatReasonSelection(reasonId: ZeroVatReasonId, reasonText: string) {
        if (!reasonText?.trim()) {
            return;
        }
        void this.invoiceService.recordVatReasonSelections([{
            documentId: this.invoiceContext.selectedDocument?.id,
            selectedTaxCategoryId: reasonId,
            writtenReason: reasonText.trim(),
            duringDraft: true,
        }]).catch(error => {
            console.warn('Failed to record draft VAT reason selection', error);
        });
    }

    getSuggestedVatReasonText(reasonId: ZeroVatReasonId, line?: UBLLine): string | undefined {
        return getSharedVatReasonText(this.invoiceContext.selectedInvoice, reasonId, line);
    }

    syncSharedVatReasonText(reasonId: ZeroVatReasonId, reasonText: string, line?: UBLLine) {
        applySharedVatReasonText(this.invoiceContext.selectedInvoice, reasonId, reasonText, line);
    }
}

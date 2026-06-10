import {bindable, IEventAggregator} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {I18N} from "@aurelia/i18n";
import {CompanyService} from "../../../../services/app/company-service";
import {AlertType} from "../../../../components/alert/alert";
import {ClassifiedTaxCategory, UBLLine} from "../../../../services/peppol/ubl";
import {
    createZeroVatCategory,
    getVatRulesetLabelKey,
    getZeroVatReasonLabelKey,
    VAT_RULESET_OPTIONS,
    VatRuleset,
    ZERO_VAT_REASON_OPTIONS,
    ZeroVatReasonId
} from "../../../../services/app/vat-rules";

export class InvoiceZeroVatReasonModal {
    private readonly companyService = resolve(CompanyService);
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);

    @bindable readOnly;
    @bindable calcLineTotal: (line: UBLLine) => void;

    open = false;
    line: UBLLine | undefined;
    reasonId: ZeroVatReasonId | undefined = undefined;
    reasonText = '';
    selectedAccountRuleset: VatRuleset = 'VAT_REGISTERED';
    private previousTaxCategory: ClassifiedTaxCategory | undefined;
    zeroVatReasonOptions = ZERO_VAT_REASON_OPTIONS;
    vatRulesetOptions = VAT_RULESET_OPTIONS;

    showModal(line: UBLLine, taxCategory?: ClassifiedTaxCategory, previousTaxCategory?: ClassifiedTaxCategory) {
        const currentTaxCategory = taxCategory ?? line.Item.ClassifiedTaxCategory;
        this.line = line;
        this.reasonId = this.zeroVatReasonOptions.includes(currentTaxCategory?.ID as ZeroVatReasonId)
            ? currentTaxCategory?.ID as ZeroVatReasonId
            : undefined;
        this.reasonText = currentTaxCategory?.TaxExemptionReason ?? '';
        this.selectedAccountRuleset = this.companyService.myCompany?.vatRuleset ?? 'VAT_REGISTERED';
        this.previousTaxCategory = previousTaxCategory;
        this.open = true;
    }

    closeModal() {
        if (this.previousTaxCategory && this.line) {
            this.line.Item.ClassifiedTaxCategory = this.previousTaxCategory;
            this.calcLineTotal?.(this.line);
        }
        this.resetModal();
    }

    async save() {
        if (!this.line || !this.reasonId) {
            return;
        }
        this.line.Item.ClassifiedTaxCategory = {
            ...createZeroVatCategory(this.reasonId),
            TaxExemptionReason: this.reasonText
        };
        if (this.reasonId === 'E' && this.selectedAccountRuleset !== (this.companyService.myCompany?.vatRuleset ?? 'VAT_REGISTERED')) {
            await this.applyAccountRuleset(this.selectedAccountRuleset);
        }
        this.calcLineTotal?.(this.line);
        this.resetModal();
    }

    getZeroVatReasonLabelKey(reasonId: ZeroVatReasonId) {
        return getZeroVatReasonLabelKey(reasonId);
    }

    getVatRulesetLabelKey(vatRuleset: VatRuleset) {
        return getVatRulesetLabelKey(vatRuleset);
    }

    async applyAccountRuleset(selectedRuleset: VatRuleset) {
        const company = JSON.parse(JSON.stringify(this.companyService.myCompany));
        company.vatRuleset = selectedRuleset;
        try {
            await this.companyService.updateCompany(company);
            this.ea.publish('alert', {alertType: AlertType.Success, text: 'Account VAT ruleset updated'});
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: 'Account VAT ruleset could not be updated'});
        }
    }

    private resetModal() {
        this.open = false;
        this.line = undefined;
        this.reasonId = undefined;
        this.reasonText = '';
        this.selectedAccountRuleset = 'VAT_REGISTERED';
        this.previousTaxCategory = undefined;
    }
}

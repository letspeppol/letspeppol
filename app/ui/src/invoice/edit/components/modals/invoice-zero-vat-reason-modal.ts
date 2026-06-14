import {bindable} from "aurelia";
import {ClassifiedTaxCategory, UBLLine} from "../../../../services/peppol/ubl";
import {
    createZeroVatCategory,
    getZeroVatReasonLabelKey,
    ZERO_VAT_REASON_OPTIONS,
    ZeroVatReasonId,
} from "../../../../services/app/vat-rules";

export class InvoiceZeroVatReasonModal {
    @bindable readOnly;
    @bindable calcLineTotal: (line: UBLLine) => void;

    open = false;
    line: UBLLine | undefined;
    reasonId: ZeroVatReasonId | undefined = undefined;
    reasonText = '';
    private previousTaxCategory: ClassifiedTaxCategory | undefined;
    zeroVatReasonOptions = ZERO_VAT_REASON_OPTIONS;

    showModal(line: UBLLine, taxCategory?: ClassifiedTaxCategory, previousTaxCategory?: ClassifiedTaxCategory) {
        const currentTaxCategory = taxCategory ?? line.Item.ClassifiedTaxCategory;
        this.line = line;
        this.reasonId = this.zeroVatReasonOptions.includes(currentTaxCategory?.ID as ZeroVatReasonId)
            ? currentTaxCategory?.ID as ZeroVatReasonId
            : undefined;
        this.reasonText = currentTaxCategory?.TaxExemptionReason ?? '';
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

    save() {
        if (!this.line || !this.reasonId) {
            return;
        }
        this.line.Item.ClassifiedTaxCategory = {
            ...createZeroVatCategory(this.reasonId),
            TaxExemptionReason: this.reasonText.trim()
        };
        if (this.reasonId === 'Z') {
            this.line.Item.ClassifiedTaxCategory.TaxExemptionReasonCode = undefined;
        }
        this.calcLineTotal?.(this.line);
        this.resetModal();
    }

    getZeroVatReasonLabelKey(reasonId: ZeroVatReasonId) {
        return getZeroVatReasonLabelKey(reasonId);
    }

    private resetModal() {
        this.open = false;
        this.line = undefined;
        this.reasonId = undefined;
        this.reasonText = '';
        this.previousTaxCategory = undefined;
    }
}

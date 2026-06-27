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
    @bindable recordVatReasonSelection: (reasonId: ZeroVatReasonId, reasonText: string) => void | Promise<void>;
    @bindable getSuggestedVatReasonText: (reasonId: ZeroVatReasonId, line?: UBLLine) => string | undefined;
    @bindable syncSharedVatReasonText: (reasonId: ZeroVatReasonId, reasonText: string, line?: UBLLine) => void;

    open = false;
    line: UBLLine | undefined;
    reasonId: ZeroVatReasonId | undefined = undefined;
    reasonText = '';
    private previousTaxCategory: ClassifiedTaxCategory | undefined;
    zeroVatReasonOptions = ZERO_VAT_REASON_OPTIONS;

    showModal(line: UBLLine, taxCategory?: ClassifiedTaxCategory, previousTaxCategory?: ClassifiedTaxCategory) {
        if (this.readOnly) {
            return;
        }
        const currentTaxCategory = taxCategory ?? line.Item.ClassifiedTaxCategory;
        this.line = line;
        this.reasonId = this.zeroVatReasonOptions.includes(currentTaxCategory?.ID as ZeroVatReasonId)
            ? currentTaxCategory?.ID as ZeroVatReasonId
            : undefined;
        this.reasonText = currentTaxCategory?.TaxExemptionReason ?? '';
        this.previousTaxCategory = previousTaxCategory ? structuredClone(previousTaxCategory) : undefined;
        this.prefillSuggestedReasonText();
        this.open = true;
    }

    updateReasonId(value: string) {
        this.reasonId = value ? value as ZeroVatReasonId : undefined;
        this.prefillSuggestedReasonText();
    }

    closeModal() {
        if (this.previousTaxCategory && this.line) {
            this.line.Item.ClassifiedTaxCategory = this.previousTaxCategory;
            this.calcLineTotal?.(this.line);
        }
        this.resetModal();
    }

    save() {
        if (this.readOnly || !this.line || !this.reasonId) {
            return;
        }
        const trimmedReasonText = this.reasonText.trim();
        this.line.Item.ClassifiedTaxCategory = {
            ...createZeroVatCategory(this.reasonId),
            TaxExemptionReason: trimmedReasonText
        };
        if (this.reasonId === 'Z') {
            this.line.Item.ClassifiedTaxCategory.TaxExemptionReasonCode = undefined;
        }
        if (trimmedReasonText) {
            this.syncSharedVatReasonText?.(this.reasonId, trimmedReasonText, this.line);
        }
        this.calcLineTotal?.(this.line);
        if (trimmedReasonText) {
            void Promise.resolve(this.recordVatReasonSelection?.(this.reasonId, trimmedReasonText)).catch(error => {
                console.warn('Failed to record VAT reason selection', error);
            });
        }
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

    private prefillSuggestedReasonText() {
        if (!this.reasonId || this.reasonText.trim()) {
            return;
        }

        const suggestedReasonText = this.getSuggestedVatReasonText?.(this.reasonId, this.line)?.trim();
        if (suggestedReasonText) {
            this.reasonText = suggestedReasonText;
        }
    }
}

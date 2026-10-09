import {bindable, BindingMode, observable} from "aurelia";
import {
    DEFAULT_PAYMENT_TERM,
    MAX_PAYMENT_TERM_DAYS,
    PAYMENT_TERM_BASES,
    PaymentTermBasis,
    formatPaymentTermCode,
    parsePaymentTermCode,
} from "../../services/app/payment-terms";

export class PaymentTermsField {
    @bindable({mode: BindingMode.twoWay}) value: string;
    @bindable disabled: boolean = false;

    @observable days: string = '';
    @observable basis: PaymentTermBasis = DEFAULT_PAYMENT_TERM.basis;

    readonly bases = PAYMENT_TERM_BASES;
    readonly maxDays = MAX_PAYMENT_TERM_DAYS;

    private loading = false;

    binding() {
        this.readValue();
    }

    valueChanged() {
        this.readValue();
    }

    daysChanged() {
        this.writeValue();
    }

    basisChanged() {
        if (!this.loading && !this.days.trim()) {
            this.days = '0';
            return;
        }
        this.writeValue();
    }

    private readValue() {
        const term = parsePaymentTermCode(this.value);
        this.loading = true;
        this.days = term ? String(term.days) : '';
        this.basis = term ? term.basis : DEFAULT_PAYMENT_TERM.basis;
        this.loading = false;
    }

    private writeValue() {
        if (this.loading) {
            return;
        }
        const days = this.parseDays();
        this.value = days === undefined ? '' : formatPaymentTermCode({days, basis: this.basis});
    }

    private parseDays(): number | undefined {
        const raw = this.days?.trim();
        if (!raw) {
            return undefined;
        }
        const days = Math.trunc(Number(raw));
        if (!Number.isFinite(days) || days < 0) {
            return undefined;
        }
        return Math.min(days, MAX_PAYMENT_TERM_DAYS);
    }
}

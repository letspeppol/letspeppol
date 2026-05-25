import {IEventAggregator} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {I18N} from "@aurelia/i18n";
import {CompanyService} from "../../services/app/company-service";
import {SponsorService} from "../../services/app/sponsor-service";
import {AlertType} from "../alert/alert";

export class SponsorPaymentModal {
    private static readonly DEFAULT_SPONSOR_NAME = "Incognito";

    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);
    private readonly companyService = resolve(CompanyService);
    private readonly sponsorService = resolve(SponsorService);

    open = false;
    sending = false;
    sent = false;
    fixedAmounts = [5, 10, 15, 25, 50, 100];
    amount = 10;
    selectedFixedAmount: number | null = 10;
    currency = "EUR";
    name = "";
    message = "";
    successTitle = "";
    successText = "";

    get vatAmount() {
        return this.round(this.validAmount * 0.21);
    }

    get totalAmount() {
        return this.round(this.validAmount + this.vatAmount);
    }

    totalFor(amount: number) {
        return this.round(amount * 1.21);
    }

    get validAmount() {
        const value = Number(this.amount);
        return Number.isFinite(value) && value >= 1 ? this.round(value) : 0;
    }

    get overSelfServiceLimit() {
        return this.validAmount > 2500;
    }

    get sponsorName() {
        return this.name?.trim() || SponsorPaymentModal.DEFAULT_SPONSOR_NAME;
    }

    async showModal() {
        this.sent = false;
        this.sending = false;
        this.successTitle = "";
        this.successText = "";
        this.message = this.i18n.tr("donations.sponsor-payment.default-message");
        await this.loadCompanyName();
        this.open = true;
    }

    closeModal() {
        this.open = false;
    }

    selectAmount(amount: number) {
        this.amount = amount;
        this.selectedFixedAmount = amount;
    }

    amountChanged(value: string) {
        const parsed = Number(value);
        if (!Number.isFinite(parsed)) {
            this.amount = 1;
            this.selectedFixedAmount = null;
            return;
        }
        this.amount = Math.max(1, this.round(parsed));
        this.selectedFixedAmount = this.fixedAmounts.includes(this.amount) ? this.amount : null;
    }

    amountSelected(amount: number) {
        return Number(this.amount) === amount;
    }

    async sponsor() {
        if (!this.validAmount || this.sending) {
            return;
        }
        this.sending = true;
        try {
            this.name = this.sponsorName;
            const response = await this.sponsorService.sponsor({
                amount: this.validAmount,
                currency: this.currency,
                name: this.sponsorName,
                message: this.message?.trim() || this.i18n.tr("donations.sponsor-payment.default-message")
            });
            if (response.status === "PACKAGE_REQUESTED") {
                this.showSuccess(this.i18n.tr("donations.sponsor-payment.success-title"), response.message);
            } else {
                this.showSuccess(
                    this.i18n.tr("donations.sponsor-payment.success-title"),
                    this.i18n.tr("donations.sponsor-payment.success-invoice-created")
                );
            }
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr("donations.sponsor-payment.success-alert")});
        } catch (error) {
            if (error instanceof Response && error.status === 409) {
                this.showSuccess(
                    this.i18n.tr("donations.sponsor-payment.success-title"),
                    this.i18n.tr("donations.sponsor-payment.duplicate-invoice")
                );
                return;
            }
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr("donations.sponsor-payment.error-alert")});
        } finally {
            this.sending = false;
        }
    }

    private showSuccess(title: string, text: string) {
        this.successTitle = title;
        this.successText = text;
        this.sent = true;
    }

    private async loadCompanyName() {
        try {
            const company = this.companyService.myCompany || await this.companyService.getAndSetMyCompanyForToken();
            this.name = company.displayName || company.name;
        } catch {
            this.name = "";
        }
    }

    private round(value: number) {
        return Math.round(value * 100) / 100;
    }
}

import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {I18N} from "@aurelia/i18n";
import {AlertType} from "../components/alert/alert";
import {ChangePasswordModal} from "../account/change-password-modal";
import {toErrorResponse, toLocalizedErrorMessage} from "../app/util/error-response-handler";
import {ConfirmationModalContext} from "../components/confirmation/confirmation-modal-context";
import {OwnershipService} from "../services/app/ownership-service";
import {LoginService} from "../services/app/login-service";
import {ThemeService} from "../services/app/theme-service";
import {RegistrationReviewService, RegistrationReviewDecisionResponse, RegistrationReviewDto, ReviewStatus} from "../services/kyc/registration-review-service";

export class Backoffice {
    private readonly registrationReviewService = resolve(RegistrationReviewService);
    private readonly ownershipService = resolve(OwnershipService);
    private readonly loginService = resolve(LoginService);
    readonly theme = resolve(ThemeService);
    private readonly confirmationModalContext = resolve(ConfirmationModalContext);
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);

    readonly statuses: ReviewStatus[] = ["PENDING", "APPROVED", "REJECTED"];
    status: ReviewStatus = "PENDING";
    reviews: RegistrationReviewDto[] = [];
    isLoading = false;
    busyId?: number;
    changePasswordModal: ChangePasswordModal;

    get hasCompany(): boolean {
        return this.ownershipService.getCurrentPeppolId() !== null;
    }

    get appPath(): string | null {
        return this.ownershipService.getCurrentPeppolId() ? this.loginService.getHomeRoute() : null;
    }

    get appCompanyName(): string | undefined {
        const peppolId = this.ownershipService.getCurrentPeppolId();
        return this.ownershipService.getCachedOwnerships().find(ownership => ownership.peppolId === peppolId)?.companyName;
    }

    logout() {
        this.loginService.logout();
    }

    async attached() {
        await this.loadReviews();
    }

    async selectStatus(status: ReviewStatus) {
        this.status = status;
        await this.loadReviews();
    }

    async downloadContract(review: RegistrationReviewDto) {
        try {
            const blob = await this.registrationReviewService.downloadContract(review.id);
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement("a");
            anchor.href = url;
            anchor.download = `contract_${review.peppolId.replace(":", "_")}_${review.id}.pdf`;
            document.body.appendChild(anchor);
            anchor.click();
            anchor.remove();
            URL.revokeObjectURL(url);
        } catch {
            this.publish(AlertType.Danger, "alert.backoffice.contract-failed");
        }
    }

    approve(review: RegistrationReviewDto) {
        this.confirmationModalContext.showConfirmationModal(
            this.i18n.tr("backoffice.approve-title"),
            this.i18n.tr(review.requestedType === "ADMIN" ? "backoffice.approve-confirm" : "backoffice.approve-confirm-without-activation",
                {signer: review.signerName, director: review.directorName, company: review.companyName}),
            () => this.decide(review, () => this.registrationReviewService.approve(review.id)),
            undefined
        );
    }

    reject(review: RegistrationReviewDto) {
        this.confirmationModalContext.showConfirmationModal(
            this.i18n.tr("backoffice.reject-title"),
            this.i18n.tr("backoffice.reject-confirm", {signer: review.signerName, company: review.companyName}),
            () => this.decide(review, () => this.registrationReviewService.reject(review.id)),
            undefined
        );
    }

    private async decide(review: RegistrationReviewDto, decision: () => Promise<RegistrationReviewDecisionResponse>) {
        this.busyId = review.id;
        try {
            const response = await decision();
            this.publish(AlertType.Success, decisionAlertKey(response));
        } catch (error) {
            const text = toLocalizedErrorMessage(await toErrorResponse(error), this.i18n, this.i18n.tr("alert.backoffice.decision-failed"));
            this.ea.publish("alert", {alertType: AlertType.Danger, text});
        } finally {
            this.busyId = undefined;
        }
        await this.loadReviews();
    }

    private async loadReviews() {
        this.isLoading = true;
        try {
            this.reviews = await this.registrationReviewService.getReviews(this.status);
        } catch {
            this.reviews = [];
            this.publish(AlertType.Danger, "alert.backoffice.load-failed");
        } finally {
            this.isLoading = false;
        }
    }

    private publish(alertType: AlertType, key: string) {
        this.ea.publish("alert", {alertType, text: this.i18n.tr(key)});
    }
}

export function decisionAlertKey(response: RegistrationReviewDecisionResponse): string {
    if (response.review.reviewStatus === "REJECTED") {
        return "alert.backoffice.rejected";
    }
    if (!response.review.requestedType) {
        return "alert.backoffice.approved-without-activation";
    }
    if (response.registration && !response.registration.peppolActive) {
        return "alert.backoffice.approved-not-registered";
    }
    return "alert.backoffice.approved";
}

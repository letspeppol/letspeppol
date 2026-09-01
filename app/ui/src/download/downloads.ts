import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import moment from "moment";
import {I18N} from "@aurelia/i18n";
import {AlertType} from "../components/alert/alert";
import {DownloadJobDto, DownloadJobService} from "../services/app/download-job-service";

export function validateDownloadPeriod(fromDate?: string, toDate?: string): string | undefined {
    if (!fromDate || !toDate) {
        return "download.validation.required";
    }
    if (fromDate > toDate) {
        return "download.validation.order";
    }
    if (fromDate.substring(0, 4) !== toDate.substring(0, 4)) {
        return "download.validation.same-year";
    }
    return undefined;
}

export class Downloads {
    private readonly downloadJobService = resolve(DownloadJobService);
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);
    private pollingTimer?: ReturnType<typeof setTimeout>;

    jobs: DownloadJobDto[] = [];
    fromDate = moment().startOf("year").format("YYYY-MM-DD");
    toDate = moment().format("YYYY-MM-DD");
    loading = false;
    submitting = false;

    async attached() {
        await this.loadJobs(true);
    }

    detaching() {
        this.stopPolling();
    }

    get activeJobCount() {
        return this.jobs.filter(job => job.status === "PENDING" || job.status === "PROCESSING" || job.status === "READY").length;
    }

    get validationKey(): string | undefined {
        return validateDownloadPeriod(this.fromDate, this.toDate);
    }

    get canCreate() {
        return !this.validationKey && this.activeJobCount < 5 && !this.submitting;
    }

    get dateMinimum() {
        const year = (this.fromDate || this.toDate || moment().format("YYYY-MM-DD")).substring(0, 4);
        return `${year}-01-01`;
    }

    get dateMaximum() {
        const year = (this.fromDate || this.toDate || moment().format("YYYY-MM-DD")).substring(0, 4);
        return `${year}-12-31`;
    }

    async createJob() {
        if (!this.canCreate) {
            return;
        }
        this.submitting = true;
        try {
            await this.downloadJobService.createJob(this.fromDate, this.toDate);
            await this.loadJobs();
            this.publish(AlertType.Success, "alert.download.created");
        } catch (error) {
            this.publish(AlertType.Danger, await this.creationErrorKey(error));
        } finally {
            this.submitting = false;
        }
    }

    async retryJob(job: DownloadJobDto) {
        try {
            await this.downloadJobService.retryJob(job.id);
            await this.loadJobs();
            this.publish(AlertType.Success, "alert.download.retried");
        } catch {
            this.publish(AlertType.Danger, "alert.download.retry-failed");
        }
    }

    async deleteJob(job: DownloadJobDto) {
        try {
            await this.downloadJobService.deleteJob(job.id);
            await this.loadJobs();
            this.publish(AlertType.Success, "alert.download.deleted");
        } catch {
            this.publish(AlertType.Danger, "alert.download.delete-failed");
        }
    }

    async download(job: DownloadJobDto) {
        try {
            const response = await this.downloadJobService.downloadJob(job.id);
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement("a");
            anchor.href = url;
            anchor.download = job.filename ?? `${job.fromDate.replaceAll("-", "")}-${job.toDate.replaceAll("-", "")}.zip`;
            document.body.appendChild(anchor);
            anchor.click();
            anchor.remove();
            URL.revokeObjectURL(url);
            await this.loadJobs();
        } catch {
            this.publish(AlertType.Danger, "alert.download.download-failed");
            await this.loadJobs();
        }
    }

    private async loadJobs(showError = false) {
        this.loading = true;
        try {
            this.jobs = await this.downloadJobService.getJobs();
        } catch {
            if (showError) {
                this.publish(AlertType.Danger, "alert.download.load-failed");
            }
        } finally {
            this.loading = false;
            this.schedulePolling();
        }
    }

    private schedulePolling() {
        this.stopPolling();
        if (this.jobs.some(job => job.status === "PENDING" || job.status === "PROCESSING")) {
            this.pollingTimer = setTimeout(() => this.loadJobs(), 5000);
        }
    }

    private stopPolling() {
        if (this.pollingTimer !== undefined) {
            clearTimeout(this.pollingTimer);
            this.pollingTimer = undefined;
        }
    }

    private publish(alertType: AlertType, key: string) {
        this.ea.publish("alert", {alertType, text: this.i18n.tr(key)});
    }

    private async creationErrorKey(error: unknown): Promise<string> {
        if (!(error instanceof Response)) {
            return "alert.download.create-failed";
        }
        if (error.status === 409) {
            return "alert.download.limit-reached";
        }
        if (error.status === 400) {
            try {
                const body = await error.clone().json();
                if (String(body?.message ?? "").includes("No finalized documents")) {
                    return "alert.download.empty-period";
                }
            } catch {
                // Fall through to the generic validation error.
            }
            return "alert.download.validation-failed";
        }
        return "alert.download.create-failed";
    }
}

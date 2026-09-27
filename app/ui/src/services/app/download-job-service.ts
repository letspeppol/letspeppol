import {resolve} from "@aurelia/kernel";
import {singleton} from "aurelia";
import {AppApi} from "./app-api";

export type DownloadJobStatus = "PENDING" | "PROCESSING" | "READY" | "FAILED";

export interface DownloadJobDto {
    id: number;
    fromDate: string;
    toDate: string;
    status: DownloadJobStatus;
    filename?: string;
    createdOn: string;
    completedOn?: string;
    expiresOn?: string;
    downloadCount: number;
    failureMessage?: string;
}

@singleton()
export class DownloadJobService {
    private readonly appApi = resolve(AppApi);

    async getJobs(): Promise<DownloadJobDto[]> {
        return this.appApi.httpClient.get("/sapi/download-jobs").then(response => response.json());
    }

    async createJob(fromDate: string, toDate: string): Promise<DownloadJobDto> {
        return this.appApi.httpClient.post("/sapi/download-jobs", JSON.stringify({fromDate, toDate}), {
            headers: {"Content-Type": "application/json"}
        }).then(response => response.json());
    }

    async retryJob(id: number): Promise<DownloadJobDto> {
        return this.appApi.httpClient.post(`/sapi/download-jobs/${id}/retry`).then(response => response.json());
    }

    async deleteJob(id: number): Promise<void> {
        await this.appApi.httpClient.delete(`/sapi/download-jobs/${id}`);
    }

    async downloadJob(id: number): Promise<Response> {
        return this.appApi.httpClient.get(`/sapi/download-jobs/${id}/file`);
    }
}

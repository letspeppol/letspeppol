import {resolve} from "@aurelia/kernel";
import {AppApi} from "../app/app-api";

export interface PeppolDirectoryResponse {
    version: string;
    "total-result-count": number;
    "used-result-count": number;
    "result-page-index": number;
    "result-page-count": number;
    "first-result-index": number;
    "last-result-index": number;
    "query-terms": string;
    "creation-dt": string;
    matches: Match[];
}

export interface Match {
    participantID: ParticipantID;
    docTypes?: DocType[];
    entities: Entity[];
}

export interface ParticipantID {
    scheme: string;
    value: string;
}

export interface DocType {
    scheme: string;
    value: string;
}

export interface Entity {
    name: Name[];
    countryCode: string;
    websites?: string[];
    geoInfo?: string;
    identifiers?: Identifier[];
    additionalInfo?: string;
    regDate?: string;
}

export interface Name {
    name: string,
    language: string
}

export interface Identifier {
    scheme: string;
    value: string;
}

export class PeppolDirService {
    // public peppolDirApi = resolve(PeppolDirApi);
    public appApi = resolve(AppApi);

    async findByParticipant(peppolId: string): Promise<PeppolDirectoryResponse> {
        const response = await this.appApi.httpClient.get(`/api/peppol-directory?participant=${peppolId}`);
        return response.json();
    }

    async findByName(name: string): Promise<PeppolDirectoryResponse> {
        const response = await this.appApi.httpClient.get(`/api/peppol-directory?name=${encodeURIComponent(name)}`);
        return response.json();
    }
}
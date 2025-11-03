import {PeppolDirApi} from "./peppol-dir-api";
import {resolve} from "@aurelia/kernel";

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
    name: string;
    countryCode: string;
    websites?: string[];
    geoInfo?: string;
    identifiers?: Identifier[];
    additionalInfo?: string;
    regDate?: string;
}

export interface Identifier {
    scheme: string;
    value: string;
}

export class RegistrationService {
    public peppolDirApi = resolve(PeppolDirApi);

    async findByParticipant(peppolId: string): Promise<PeppolDirectoryResponse> {
        const response = await this.peppolDirApi.httpClient.get(`/search/1.0/json?participant=iso6523-actorid-upis::${peppolId}`);
        return response.json();
    }

    async findByName(name: string): Promise<PeppolDirectoryResponse> {
        const response = await this.peppolDirApi.httpClient.get(`/search/1.0/json?name=${name}`);
        return response.json();
    }
}
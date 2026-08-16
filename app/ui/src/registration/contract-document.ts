export const SIGNING_SESSION_HEADER = 'X-Signing-Session';

export type ContractFetcher = (path: string, init: RequestInit) => Promise<Response>;

export async function retrieveContractBlob(
    fetcher: ContractFetcher,
    peppolId: string,
    directorId: number,
    signingSessionToken: string,
): Promise<Blob> {
    const response = await fetcher(
        `/api/identity/contract/${encodeURIComponent(peppolId)}/${directorId}`,
        {headers: {[SIGNING_SESSION_HEADER]: signingSessionToken}},
    );
    if (!response.ok) {
        throw response;
    }
    return response.blob();
}

export class ContractDocumentUrl {
    private objectUrl: string | null = null;

    get value(): string {
        return this.objectUrl ? `${this.objectUrl}#page=1&view=FitH,300` : '';
    }

    setBlob(blob: Blob): void {
        this.clear();
        this.objectUrl = window.URL.createObjectURL(blob);
    }

    clear(): void {
        if (this.objectUrl) {
            window.URL.revokeObjectURL(this.objectUrl);
            this.objectUrl = null;
        }
    }
}

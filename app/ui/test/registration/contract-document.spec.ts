import {afterEach, describe, expect, it, vi} from 'vitest';
import {
    ContractDocumentUrl,
    retrieveContractBlob,
    SIGNING_SESSION_HEADER,
} from '../../src/registration/contract-document';

afterEach(() => {
    vi.restoreAllMocks();
});

describe('contract signing document', () => {
    it('retrieves the PDF as a blob with the in-memory signing session header', async () => {
        const expected = new Blob(['prepared contract'], {type: 'application/pdf'});
        const response = {
            ok: true,
            blob: vi.fn().mockResolvedValue(expected),
        } as unknown as Response;
        const fetcher = vi.fn().mockResolvedValue(response);

        const result = await retrieveContractBlob(fetcher, '0208:123/45', 42, 'opaque-session');

        expect(result).toBe(expected);
        expect(fetcher).toHaveBeenCalledWith(
            '/api/identity/contract/0208%3A123%2F45/42',
            {headers: {[SIGNING_SESSION_HEADER]: 'opaque-session'}},
        );
    });

    it('rejects failed retrieval without creating an object URL', async () => {
        const failure = new Response(null, {status: 404});
        const fetcher = vi.fn().mockResolvedValue(failure);

        await expect(retrieveContractBlob(fetcher, '0208:1', 1, 'unknown')).rejects.toBe(failure);
    });

    it('revokes replaced and detached object URLs', () => {
        if (!window.URL.createObjectURL) {
            Object.defineProperty(window.URL, 'createObjectURL', {configurable: true, value: () => ''});
        }
        if (!window.URL.revokeObjectURL) {
            Object.defineProperty(window.URL, 'revokeObjectURL', {configurable: true, value: () => undefined});
        }
        const createObjectURL = vi.spyOn(window.URL, 'createObjectURL')
            .mockReturnValueOnce('blob:first')
            .mockReturnValueOnce('blob:second');
        const revokeObjectURL = vi.spyOn(window.URL, 'revokeObjectURL').mockImplementation(() => undefined);
        const documentUrl = new ContractDocumentUrl();

        documentUrl.setBlob(new Blob(['first']));
        expect(documentUrl.value).toBe('blob:first#page=1&view=FitH,300');

        documentUrl.setBlob(new Blob(['second']));
        expect(revokeObjectURL).toHaveBeenCalledWith('blob:first');
        expect(documentUrl.value).toBe('blob:second#page=1&view=FitH,300');

        documentUrl.clear();
        documentUrl.clear();
        expect(revokeObjectURL).toHaveBeenCalledWith('blob:second');
        expect(revokeObjectURL).toHaveBeenCalledTimes(2);
        expect(createObjectURL).toHaveBeenCalledTimes(2);
        expect(documentUrl.value).toBe('');
    });
});

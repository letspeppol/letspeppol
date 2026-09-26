import {describe, expect, it} from 'vitest';
import {
    deserializeRequestOptions,
    serializeAuthenticationCredential,
} from '../../src/services/kyc/webauthn-utils';

describe('WebAuthn authentication utilities', () => {
    it('decodes request challenges and allowed credential IDs', () => {
        const options = deserializeRequestOptions({
            challenge: 'AQID',
            rpId: 'example.com',
            timeout: 60_000,
            userVerification: 'required',
            allowCredentials: [{
                type: 'public-key',
                id: 'BAUG',
                transports: ['internal'],
            }],
        });

        expect(Array.from(new Uint8Array(options.challenge))).toEqual([1, 2, 3]);
        expect(Array.from(new Uint8Array(options.allowCredentials?.[0].id ?? new ArrayBuffer(0))))
            .toEqual([4, 5, 6]);
        expect(options.allowCredentials?.[0].transports).toEqual(['internal']);
    });

    it('serializes an authentication assertion for the KYC verify endpoint', () => {
        const credential = {
            id: 'credential-id',
            rawId: new Uint8Array([1, 2]).buffer,
            type: 'public-key',
            response: {
                clientDataJSON: new Uint8Array([3, 4]).buffer,
                authenticatorData: new Uint8Array([5, 6]).buffer,
                signature: new Uint8Array([7, 8]).buffer,
                userHandle: new Uint8Array([9]).buffer,
            },
        } as unknown as PublicKeyCredential;

        expect(serializeAuthenticationCredential(credential)).toEqual({
            id: 'AQI',
            rawId: 'AQI',
            type: 'public-key',
            clientDataJSON: 'AwQ',
            authenticatorData: 'BQY',
            signature: 'Bwg',
            userHandle: 'CQ',
        });
    });
});

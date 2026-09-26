export function base64urlEncode(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let str = '';
    for (let i = 0; i < bytes.length; i++) str += String.fromCharCode(bytes[i]);
    return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

export function base64urlDecode(str: string): ArrayBuffer {
    str = str.replace(/-/g, '+').replace(/_/g, '/');
    while (str.length % 4) str += '=';
    const binary = atob(str);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes.buffer;
}

interface EncodedCredentialDescriptor {
    type: PublicKeyCredentialType;
    id: string;
    transports?: AuthenticatorTransport[];
}

interface EncodedCreationOptions {
    challenge: string;
    rp: PublicKeyCredentialRpEntity;
    user: Omit<PublicKeyCredentialUserEntity, 'id'> & {id: string};
    pubKeyCredParams: PublicKeyCredentialParameters[];
    timeout?: number;
    excludeCredentials?: EncodedCredentialDescriptor[];
    authenticatorSelection?: AuthenticatorSelectionCriteria;
    attestation?: AttestationConveyancePreference;
}

interface EncodedRequestOptions {
    challenge: string;
    rpId?: string;
    timeout?: number;
    userVerification?: UserVerificationRequirement;
    allowCredentials?: EncodedCredentialDescriptor[];
}

export function deserializeCreationOptions(json: EncodedCreationOptions): PublicKeyCredentialCreationOptions {
    return {
        challenge: base64urlDecode(json.challenge),
        rp: json.rp,
        user: {
            ...json.user,
            id: base64urlDecode(json.user.id),
        },
        pubKeyCredParams: json.pubKeyCredParams,
        timeout: json.timeout,
        excludeCredentials: (json.excludeCredentials || []).map(credential => ({
            type: credential.type,
            id: base64urlDecode(credential.id),
            transports: credential.transports,
        })),
        authenticatorSelection: json.authenticatorSelection,
        attestation: json.attestation,
    };
}

export function deserializeRequestOptions(json: EncodedRequestOptions): PublicKeyCredentialRequestOptions {
    return {
        challenge: base64urlDecode(json.challenge),
        rpId: json.rpId,
        timeout: json.timeout,
        userVerification: json.userVerification,
        allowCredentials: (json.allowCredentials || []).map(credential => ({
            type: credential.type,
            id: base64urlDecode(credential.id),
            transports: credential.transports,
        })),
    };
}

export function serializeRegistrationCredential(credential: PublicKeyCredential): object {
    const response = credential.response as AuthenticatorAttestationResponse;
    return {
        id: base64urlEncode(credential.rawId),
        rawId: base64urlEncode(credential.rawId),
        type: credential.type,
        clientDataJSON: base64urlEncode(response.clientDataJSON),
        attestationObject: base64urlEncode(response.attestationObject),
        transports: typeof response.getTransports === 'function' ? response.getTransports() : [],
    };
}

export function serializeAuthenticationCredential(credential: PublicKeyCredential): object {
    const response = credential.response as AuthenticatorAssertionResponse;
    const credentialId = base64urlEncode(credential.rawId);
    return {
        id: credentialId,
        rawId: credentialId,
        type: credential.type,
        clientDataJSON: base64urlEncode(response.clientDataJSON),
        authenticatorData: base64urlEncode(response.authenticatorData),
        signature: base64urlEncode(response.signature),
        userHandle: response.userHandle ? base64urlEncode(response.userHandle) : null,
    };
}

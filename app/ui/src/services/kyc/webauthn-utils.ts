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

export function deserializeCreationOptions(json: any): PublicKeyCredentialCreationOptions {
    return {
        challenge: base64urlDecode(json.challenge),
        rp: json.rp,
        user: {
            ...json.user,
            id: base64urlDecode(json.user.id),
        },
        pubKeyCredParams: json.pubKeyCredParams,
        timeout: json.timeout,
        excludeCredentials: (json.excludeCredentials || []).map((c: any) => ({
            type: c.type,
            id: base64urlDecode(c.id),
            transports: c.transports,
        })),
        authenticatorSelection: json.authenticatorSelection,
        attestation: json.attestation,
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

package org.letspeppol.kyc.dto;

public record PasskeyAuthenticationResponse(
        String id,
        String rawId,
        String type,
        String clientDataJSON,
        String authenticatorData,
        String signature,
        String userHandle
) {}

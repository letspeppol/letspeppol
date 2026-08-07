package org.letspeppol.kyc.dto;

public record PasskeyVerifyRegistrationRequest(
        String challengeToken,
        String displayName,
        PasskeyRegistrationResponse credential
) {}

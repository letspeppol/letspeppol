package org.letspeppol.kyc.dto;

public record PrepareSigningResponse(
        String hashToSign,
        String hashToFinalize,
        String hashFunction
) {}

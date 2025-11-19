package org.letspeppol.kyc.dto;

public record FinalizeSigningRequest(
    String emailToken,
    Long directorId,
    String certificate,
    String signature,
    SignatureAlgorithm signatureAlgorithm,
    String hashToSign,
    String hashToFinalize,
    String password
) {
}

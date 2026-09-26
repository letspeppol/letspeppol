package org.letspeppol.kyc.dto;

public record FinalizeSigningRequest(
    String peppolId,
    Long directorId,
    String email,
    String certificate,
    String signature,
    SignatureAlgorithm signatureAlgorithm,
    String hashToSign,
    String hashToFinalize
) {
}

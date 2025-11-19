package org.letspeppol.kyc.dto;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.List;

public record PrepareSigningRequest(
        String emailToken,
        Long directorId,
        String certificate,
        List<SignatureAlgorithm> supportedSignatureAlgorithms,
        String language
) {

    public String sha256() {
        String stringToHash = certificate + emailToken + directorId;
        return DigestUtils.sha256Hex(stringToHash);
    }
}

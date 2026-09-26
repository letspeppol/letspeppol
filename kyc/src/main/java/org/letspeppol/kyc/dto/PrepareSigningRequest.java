package org.letspeppol.kyc.dto;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.List;

public record PrepareSigningRequest(
        String peppolId,
        Long directorId,
        String certificate,
        List<SignatureAlgorithm> supportedSignatureAlgorithms,
        String language
) {

    public String sha256() {
        String stringToHash = certificate + peppolId + directorId;
        return DigestUtils.sha256Hex(stringToHash);
    }
}

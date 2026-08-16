package org.letspeppol.kyc.dto;

import java.util.List;

public record PrepareSigningRequest(
        String peppolId,
        Long directorId,
        String certificate,
        List<SignatureAlgorithm> supportedSignatureAlgorithms,
        String language
) {}

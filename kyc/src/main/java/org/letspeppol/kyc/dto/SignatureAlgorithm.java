package org.letspeppol.kyc.dto;

public record SignatureAlgorithm (
        String hashFunction,
        String paddingScheme,
        String cryptoAlgorithm
) {}

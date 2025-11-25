package org.letspeppol.kyc.dto;

public record RegistrationRequest(
        String name,
        String language,
        String country
) {}
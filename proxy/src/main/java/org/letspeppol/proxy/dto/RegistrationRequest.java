package org.letspeppol.proxy.dto;

public record RegistrationRequest(
        String name,
        String language,
        String country
) {}
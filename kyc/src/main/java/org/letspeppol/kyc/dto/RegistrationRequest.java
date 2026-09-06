package org.letspeppol.kyc.dto;

public record RegistrationRequest(
        String name,
        String language,
        String country,
        String address,
        String postalCode,
        String city,
        String vatNumber
) {
    public RegistrationRequest(String name, String language, String country) {
        this(name, language, country, null, null, null, null);
    }
}

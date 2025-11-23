package org.letspeppol.app.dto;

public record RegistrationRequest(
        String peppolId,
        String vatNumber,
        String companyName,
        String street,
        String houseNumber,
        String city,
        String postalCode,
        String directorName,
        String directorEmail
) {}

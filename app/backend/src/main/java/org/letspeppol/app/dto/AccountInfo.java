package org.letspeppol.app.dto;

public record AccountInfo(
        String peppolId,
        String identifier,
        String vatNumber,
        String companyName,
        String street,
        String city,
        String postalCode,
        String directorName,
        String directorEmail
) {}

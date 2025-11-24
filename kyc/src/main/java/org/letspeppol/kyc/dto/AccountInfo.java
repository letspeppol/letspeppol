package org.letspeppol.kyc.dto;

/**
 * Sent to App backend
 */
public record AccountInfo(
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

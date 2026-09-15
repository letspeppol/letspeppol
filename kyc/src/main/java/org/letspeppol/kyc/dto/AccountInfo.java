package org.letspeppol.kyc.dto;

import java.time.Instant;

/**
 * Sent to App backend
 */
public record AccountInfo(
        String peppolId,
        String identifier,
        String vatNumber,
        String companyName,
        String street,
        String city,
        String postalCode,
        String directorName,
        String directorEmail,
        boolean active,
        Instant lastUpdatedTimestamp
) {}

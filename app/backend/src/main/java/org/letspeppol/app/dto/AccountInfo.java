package org.letspeppol.app.dto;

import java.time.Instant;

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

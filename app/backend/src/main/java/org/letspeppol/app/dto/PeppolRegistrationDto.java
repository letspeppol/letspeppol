package org.letspeppol.app.dto;

public record PeppolRegistrationDto(
        String peppolId,
        boolean peppolActive,
        String accessPoint
) {}

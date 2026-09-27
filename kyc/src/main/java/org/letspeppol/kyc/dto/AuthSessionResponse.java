package org.letspeppol.kyc.dto;

public record AuthSessionResponse(
        String status,
        String csrfToken,
        String csrfHeaderName,
        String csrfParameterName
) {}

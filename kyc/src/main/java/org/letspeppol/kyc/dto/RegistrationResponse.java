package org.letspeppol.kyc.dto;

public record RegistrationResponse(
        Boolean peppolActive,
        String errorCode,
        String body
) {}

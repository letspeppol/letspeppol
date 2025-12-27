package org.letspeppol.kyc.dto;

public record FinalizeSigningResponse(
        byte[] pdfBytes,
        RegistrationResponse registrationResponse
) {}

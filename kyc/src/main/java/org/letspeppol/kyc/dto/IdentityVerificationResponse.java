package org.letspeppol.kyc.dto;

import org.letspeppol.kyc.model.Account;

public record IdentityVerificationResponse(
        Account account,
        RegistrationResponse registrationResponse
) {}


package org.letspeppol.kyc.dto;

import org.letspeppol.kyc.model.AccountType;

public record TokenVerificationResponse(
        String email,
        boolean accountExists,
        boolean accountVerified,
        boolean directorSigned,
        AccountType requestedType,
        CompanyResponse company,
        OwnershipInfo requester
) {
}

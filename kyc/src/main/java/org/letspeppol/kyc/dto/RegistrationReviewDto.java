package org.letspeppol.kyc.dto;

import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.ReviewStatus;

import java.time.Instant;

public record RegistrationReviewDto(
        Long id,
        Instant createdOn,
        String accountEmail,
        boolean accountVerified,
        String peppolId,
        String companyName,
        boolean companySuspended,
        boolean companyHasAdmin,
        String directorName,
        String signerName,
        AccountType requestedType,
        ReviewStatus reviewStatus,
        String reviewedBy,
        Instant reviewedOn
) {}

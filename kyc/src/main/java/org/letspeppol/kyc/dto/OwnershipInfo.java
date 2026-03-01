package org.letspeppol.kyc.dto;

import org.letspeppol.kyc.model.AccountType;

import java.time.Instant;

public record OwnershipInfo(
        AccountType type,
        String name,
        String email,
        String company,
        Instant createdOn,
        Instant identityVerifiedOn
) {}

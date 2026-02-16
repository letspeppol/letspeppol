package org.letspeppol.kyc.dto;

import org.letspeppol.kyc.model.AccountType;

import java.time.Instant;

public record OwnershipInfo(
        AccountType type,
        String name,
        String email,
        Instant createdOn,
        Instant identityVerifiedOn
) {}

package org.letspeppol.kyc.dto;

import org.letspeppol.kyc.model.AccountType;

import java.time.Instant;
import java.util.UUID;

public record LinkedInfo(
        UUID externalId,
        AccountType type,
        String name,
        String email,
//        Instant createdOn, //is linked date
        Instant identityVerifiedOn
) {}

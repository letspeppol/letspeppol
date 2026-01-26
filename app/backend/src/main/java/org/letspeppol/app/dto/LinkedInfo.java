package org.letspeppol.app.dto;

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

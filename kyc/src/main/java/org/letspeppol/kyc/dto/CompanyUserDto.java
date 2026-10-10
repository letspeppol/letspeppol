package org.letspeppol.kyc.dto;

import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.OwnershipStatus;

import java.time.Instant;

public record CompanyUserDto(
        Long id,
        String name,
        String email,
        AccountType type,
        OwnershipStatus status,
        int permissionMask,
        Instant createdOn,
        Instant lastUsed,
        Instant invitationExpiresOn
) {}

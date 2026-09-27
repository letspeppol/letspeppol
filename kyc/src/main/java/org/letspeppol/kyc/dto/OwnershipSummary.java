package org.letspeppol.kyc.dto;

import org.letspeppol.kyc.model.AccountType;

public record OwnershipSummary(
        String peppolId,
        String companyName,
        AccountType type,
        boolean peppolActive
) {}

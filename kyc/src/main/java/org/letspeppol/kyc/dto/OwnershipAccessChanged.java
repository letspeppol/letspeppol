package org.letspeppol.kyc.dto;

import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;

public record OwnershipAccessChanged(
        String email,
        String peppolId,
        AccountType type
) {
    public static OwnershipAccessChanged of(Ownership ownership) {
        return new OwnershipAccessChanged(
                ownership.getAccount().getEmail(),
                ownership.getCompany().getPeppolId(),
                ownership.getType());
    }
}

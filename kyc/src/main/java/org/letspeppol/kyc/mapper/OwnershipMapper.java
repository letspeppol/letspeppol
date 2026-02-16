package org.letspeppol.kyc.mapper;

import org.letspeppol.kyc.dto.OwnershipInfo;
import org.letspeppol.kyc.model.Ownership;

public class OwnershipMapper {
    public static OwnershipInfo toOwnershipInfo(Ownership ownership) {
        return new OwnershipInfo(
                ownership.getType(),
                ownership.getAccount().getName(),
                ownership.getAccount().getEmail(),
                ownership.getCreatedOn(),
                ownership.getAccount().getIdentityVerifiedOn()
        );
    }
}

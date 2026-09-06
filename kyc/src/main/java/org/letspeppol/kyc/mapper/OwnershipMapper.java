package org.letspeppol.kyc.mapper;

import org.letspeppol.kyc.dto.OwnershipInfo;
import org.letspeppol.kyc.dto.OwnershipSummary;
import org.letspeppol.kyc.model.Ownership;

public class OwnershipMapper {
    public static OwnershipInfo toOwnershipInfo(Ownership ownership) {
        return new OwnershipInfo(
                ownership.getType(),
                ownership.getAccount().getName(),
                ownership.getAccount().getEmail(),
                ownership.getCompany().getName(),
                ownership.getAccount().getCreatedOn()
        );
    }

    public static OwnershipSummary toOwnershipSummary(Ownership ownership) {
        return new OwnershipSummary(
                ownership.getCompany().getPeppolId(),
                ownership.getCompany().getName(),
                ownership.getType(),
                ownership.getCompany().isPeppolActive()
        );
    }
}

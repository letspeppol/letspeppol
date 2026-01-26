package org.letspeppol.kyc.mapper;

import org.letspeppol.kyc.dto.AccountInfo;
import org.letspeppol.kyc.dto.LinkedInfo;
import org.letspeppol.kyc.model.Account;

public class AccountMapper {

    public static LinkedInfo toLinkedInfo(Account account) {
        return new LinkedInfo(
                account.getExternalId(),
                account.getType(),
                account.getName(),
                account.getEmail(),
//                account.getCreatedOn(), //TODO future : link.createdOn,
                account.getIdentityVerifiedOn()
        );
    }

    public static AccountInfo toAccountInfo(Account account) {
        return new AccountInfo(
                account.getCompany().getPeppolId(),
                account.getCompany().getVatNumber(),
                account.getCompany().getName(),
                account.getCompany().getStreet(),
                account.getCompany().getCity(),
                account.getCompany().getPostalCode(),
                account.getName(),
                account.getEmail()
        );
    }

}

package org.letspeppol.kyc.mapper;

import org.letspeppol.kyc.dto.AccountInfo;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.kbo.Company;

public class AccountMapper {

    public static AccountInfo toAccountInfo(Account account, Company company) {
        return new AccountInfo(
                company.getPeppolId(),
                company.getVatNumber(),
                company.getName(),
                company.getStreet(),
                company.getCity(),
                company.getPostalCode(),
                account.getName(),
                account.getEmail()
        );
    }

}

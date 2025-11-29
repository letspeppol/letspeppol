package org.letspeppol.app.mapper;

import org.letspeppol.app.dto.CompanyDto;
import org.letspeppol.app.model.Company;

public class CompanyMapper {

    public static CompanyDto toDto(Company company) {
        return new CompanyDto(
                company.getPeppolId(),
                company.getVatNumber(),
                company.getName(),
                company.getSubscriber(),
                company.getSubscriberEmail(),
                company.getPaymentTerms(),
                company.getIban(),
                company.getPaymentAccountName(),
                company.isRegisteredOnPeppol(),
                AddressMapper.toDto(company.getRegisteredOffice())
        );
    }

}

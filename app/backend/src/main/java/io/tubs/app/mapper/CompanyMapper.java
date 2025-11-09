package io.tubs.app.mapper;

import io.tubs.app.dto.CompanyDto;
import io.tubs.app.model.Company;

public class CompanyMapper {

    public static CompanyDto toDto(Company company) {
        return new CompanyDto(
                company.getCompanyNumber(),
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

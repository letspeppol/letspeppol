package org.letspeppol.app.mapper;

import org.letspeppol.app.dto.CompanyDto;
import org.letspeppol.app.model.Company;

public class CompanyMapper {

    public static CompanyDto toDto(Company company, boolean peppolActive) {
        return new CompanyDto(
                company.getPeppolId(),
                company.getVatNumber(),
                company.getName(),
                company.getSubscriber(),
                company.getSubscriberEmail(),
                company.getPaymentTerms(),
                company.getIban(),
                company.getPaymentAccountName(),
                company.getLastInvoiceReference(),
                // TODO                company.isNoArchive(),
                peppolActive,
                AddressMapper.toDto(company.getRegisteredOffice())
        );
    }

}

package org.letspeppol.app.dto;

public record CompanyDto(
        String companyNumber,
        String vatNumber,
        String name,
        String subscriber,
        String subscriberEmail,
        String paymentTerms,
        String iban,
        String paymentAccountName,
        boolean registeredOnPeppol,
        AddressDto registeredOffice
)
{}

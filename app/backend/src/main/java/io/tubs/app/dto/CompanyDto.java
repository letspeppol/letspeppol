package io.tubs.app.dto;

public record CompanyDto(
        String companyNumber,
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

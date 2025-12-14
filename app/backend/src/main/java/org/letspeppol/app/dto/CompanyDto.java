package org.letspeppol.app.dto;

public record CompanyDto(
        String peppolId,
        String vatNumber,
        String name,
        String subscriber,
        String subscriberEmail,
        String paymentTerms,
        String iban,
        String paymentAccountName,
        boolean noArchive,
        boolean peppolActive,
        AddressDto registeredOffice
)
{}

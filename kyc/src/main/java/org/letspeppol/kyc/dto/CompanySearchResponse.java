package org.letspeppol.kyc.dto;

public record CompanySearchResponse(
        String peppolId,
        String vatNumber,
        String name,
        String street,
        String city,
        String postalCode
) {}

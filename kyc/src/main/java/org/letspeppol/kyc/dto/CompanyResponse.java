package org.letspeppol.kyc.dto;

import java.util.List;

public record CompanyResponse(
        Long id,
        String peppolId,
        String vatNumber,
        String name,
        String street,
        String houseNumber,
        String city,
        String postalCode,
        List<DirectorDto> directors
) {}


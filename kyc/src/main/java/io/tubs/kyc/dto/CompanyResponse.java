package io.tubs.kyc.dto;

import java.util.List;

public record CompanyResponse(
        Long id,
        String companyNumber,
        String name,
        String street,
        String houseNumber,
        String city,
        String postalCode,
        List<DirectorDto> directors
) {}


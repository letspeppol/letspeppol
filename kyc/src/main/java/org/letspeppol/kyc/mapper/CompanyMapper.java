package org.letspeppol.kyc.mapper;

import org.letspeppol.kyc.dto.CompanyResponse;
import org.letspeppol.kyc.dto.CompanySearchResponse;
import org.letspeppol.kyc.dto.DirectorDto;
import org.letspeppol.kyc.model.kbo.Company;

import java.util.stream.Collectors;

public class CompanyMapper {

    public static CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getPeppolId(),
                company.getVatNumber(),
                company.getName(),
                company.getStreet(),
                company.getCity(),
                company.getPostalCode(),
                company.getDirectors().stream()
                        .map(d -> new DirectorDto(d.getId(), d.getName()))
                        .collect(Collectors.toList()),
                company.isHasKboAddress(),
                company.isRegisteredOnPeppol()
        );
    }

    public static CompanySearchResponse toSearchResponse(Company company) {
        return new CompanySearchResponse(
                company.getPeppolId(),
                company.getVatNumber(),
                company.getName(),
                company.getStreet(),
                company.getCity(),
                company.getPostalCode()
        );
    }

}

package io.tubs.kyc.service;

import io.tubs.kyc.dto.CompanyResponse;
import io.tubs.kyc.dto.DirectorDto;
import io.tubs.kyc.exception.KycErrorCodes;
import io.tubs.kyc.exception.KycException;
import io.tubs.kyc.model.kbo.Company;
import io.tubs.kyc.model.kbo.Director;
import io.tubs.kyc.repository.CompanyRepository;
import io.tubs.kyc.repository.DirectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final DirectorRepository directorRepository;
    private final KboLookupService kboLookupService;
    private final LetsPeppolProxyService letsPeppolProxyService;
    private final AppService appService;

    public Optional<CompanyResponse> getByCompanyNumber(String companyNumber) {
        Optional<Company> company = companyRepository.findByCompanyNumber(companyNumber);
        if (company.isPresent()) {
            return Optional.of(toResponse(company.get()));
        }

        Optional<CompanyResponse> companyLookup = kboLookupService.findCompany(companyNumber);
        if (companyLookup.isPresent()) {
            Company companyToStore = storeCompanyAndDirectors(companyNumber, companyLookup.get());
            return Optional.of(toResponse(companyToStore));
        }

        return Optional.empty();
    }

    private Company storeCompanyAndDirectors(String companyNumber, CompanyResponse companyResponse) {
        Company company = new Company(
                companyNumber,
                companyResponse.name(),
                companyResponse.city(),
                companyResponse.postalCode(),
                companyResponse.street(),
                companyResponse.houseNumber()
        );
        companyRepository.save(company);
        for (DirectorDto director : companyResponse.directors()) {
            Director directorToStore = new Director(director.name(), company);
            directorRepository.save(directorToStore);
        }
        return company;
    }

    public CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getCompanyNumber(),
                company.getName(),
                company.getStreet(),
                company.getHouseNumber(),
                company.getCity(),
                company.getPostalCode(),
                company.getDirectors().stream()
                        .map(d -> new DirectorDto(d.getId(), d.getName()))
                        .collect(Collectors.toList())
        );
    }

    public void unregisterCompany(String companyNumber, String token) {
        Company company = companyRepository.findByCompanyNumber(companyNumber).orElseThrow(() -> new KycException(KycErrorCodes.COMPANY_NOT_FOUND));
        company.setRegisteredOnPeppol(false);
        companyRepository.save(company);
        //letsPeppolProxyService.unregisterCompany(token);
        appService.unregister(companyNumber);
    }
}

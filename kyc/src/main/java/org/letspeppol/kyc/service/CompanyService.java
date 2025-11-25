package org.letspeppol.kyc.service;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.CompanyResponse;
import org.letspeppol.kyc.dto.DirectorDto;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.DirectorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final DirectorRepository directorRepository;
    private final KboLookupService kboLookupService;
    private final LetsPeppolProxyService letsPeppolProxyService;
    private final AppService appService;
    private final Counter companyUnregistrationCounter;

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
        companyUnregistrationCounter.increment();
    }
}

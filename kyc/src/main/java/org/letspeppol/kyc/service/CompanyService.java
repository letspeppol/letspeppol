package org.letspeppol.kyc.service;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.CompanyResponse;
import org.letspeppol.kyc.dto.DirectorDto;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.DirectorRepository;
import org.letspeppol.kyc.service.kbo.KboLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final DirectorRepository directorRepository;
    private final KboLookupService kboLookupService;
    private final JwtService jwtService;
    private final ProxyService proxyService;
    private final Counter companyUnregistrationCounter;

    public Optional<CompanyResponse> getByPeppolId(String peppolId) {
        Optional<Company> company = companyRepository.findByPeppolId(peppolId);
        if (company.isPresent()) {
            return Optional.of(toResponse(company.get()));
        }

        Optional<CompanyResponse> companyLookup = kboLookupService.findCompany(peppolId);
        if (companyLookup.isPresent()) {
            Company companyToStore = storeCompanyAndDirectors(peppolId, companyLookup.get());
            return Optional.of(toResponse(companyToStore));
        }

        return Optional.empty();
    }

    private Company storeCompanyAndDirectors(String peppolId, CompanyResponse companyResponse) {
        Company company = new Company(peppolId, companyResponse.vatNumber(), companyResponse.name());
        company.setAddress(companyResponse.city(),companyResponse.postalCode(), companyResponse.street());
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

    public boolean registerCompany(String peppolId) {
        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new KycException(KycErrorCodes.COMPANY_NOT_FOUND));
        return registerCompany(company);
    }

    public boolean registerCompany(Company company) {
        if (company.isSuspended()) {
            log.info("Will not register suspended company {}", company.getName());
            return false;
        }
        if (company.isRegisteredOnPeppol()) {
            log.info("Will skip registration for already registered company {}", company.getName());
            return true;
        }
        String token = jwtService.generateInternalToken(company.getPeppolId(), company.isPeppolActive());
        boolean peppolActive = proxyService.registerCompany(token, company.getName());
        company.setRegisteredOnPeppol(peppolActive);
        companyRepository.save(company);
        return peppolActive;
    }

    public boolean unregisterCompany(String peppolId) {
        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new KycException(KycErrorCodes.COMPANY_NOT_FOUND));
        return unregisterCompany(company);
    }

    public boolean unregisterCompany(Company company) {
        String token = jwtService.generateInternalToken(company.getPeppolId(), company.isPeppolActive());
        boolean peppolActive = proxyService.unregisterCompany(token);
        company.setRegisteredOnPeppol(peppolActive);
        companyRepository.save(company);
        if (!peppolActive) {
            companyUnregistrationCounter.increment();
        }
        return peppolActive;
    }

    public void suspendCompany(Company company) {
        company.setSuspended(true);
        companyRepository.save(company);
        if (company.isRegisteredOnPeppol()) {
            unregisterCompany(company);
        }
    }
}

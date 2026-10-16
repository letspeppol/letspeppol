package org.letspeppol.kyc.service;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.CompanyResponse;
import org.letspeppol.kyc.dto.CompanySearchResponse;
import org.letspeppol.kyc.dto.DirectorDto;
import org.letspeppol.kyc.dto.RegistrationResponse;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.mapper.CompanyMapper;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.OwnershipRepository;
import org.letspeppol.kyc.service.kbo.KboLookupService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final OwnershipRepository ownershipRepository;
    private final KboLookupService kboLookupService;
    private final ProxyService proxyService;
    private final Counter companyUnregistrationCounter;

    public List<CompanySearchResponse> search(String identifier, String vatNumber, String peppolId, String name) {
        return companyRepository.search(identifier, vatNumber, peppolId, name != null ? name.toLowerCase(Locale.ROOT) + "%" : null, Pageable.ofSize(5)).stream()
                .map(CompanyMapper::toSearchResponse)
                .toList();
    }

    public Company getByPeppolId(String peppolId) {
        return companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new KycException(KycErrorCodes.COMPANY_NOT_FOUND));
    }

    public Optional<CompanyResponse> getResponseByPeppolId(String peppolId) {
        Optional<Company> company = companyRepository.findByPeppolId(peppolId);
        if (company.isPresent()) {
            boolean hasAdmin = ownershipRepository.existsByTypeAndCompanyPeppolId(AccountType.ADMIN, peppolId);
            return Optional.of(CompanyMapper.toResponse(company.get(), hasAdmin)); //TODO : maybe not returning directors, but only when request originates from AFFILIATE ?
        }

        Optional<CompanyResponse> companyLookup = kboLookupService.findCompany(peppolId); //TODO: what with inactive ?
        if (companyLookup.isPresent()) {
            Company companyToStore = storeCompanyAndDirectors(peppolId, companyLookup.get());
            return Optional.of(CompanyMapper.toResponse(companyToStore, false)); //TODO : maybe not returning directors, but only when request originates from AFFILIATE ?
        }

        return Optional.empty();
    }

    private Company storeCompanyAndDirectors(String peppolId, CompanyResponse companyResponse) {
        Company company = new Company(peppolId, companyResponse.identifier(), companyResponse.vatNumber(), companyResponse.name());
        company.setAddress(companyResponse.city(),companyResponse.postalCode(), companyResponse.street());
        for (DirectorDto director : companyResponse.directors()) {
            company.addDirector(new Director(director.name(), company));
        }
        return companyRepository.save(company);
    }

    public RegistrationResponse registerCompany(String peppolId) {
        Company company = getByPeppolId(peppolId);
        return registerCompany(company);
    }

    public RegistrationResponse registerCompany(Company company) {
        if (company.isSuspended()) {
            log.info("Will not register suspended company {}", company.getName());
            return new RegistrationResponse(false, KycErrorCodes.PROXY_REGISTRATION_SUSPENDED, "Account is currently suspended");
        }
        if (company.isRegisteredOnPeppol()) {
            log.info("Will skip registration for already registered company {}", company.getName());
            return new RegistrationResponse(true, KycErrorCodes.PROXY_REGISTRATION_NOT_NEEDED, "Account is already registered");
        }
        RegistrationResponse registrationResponse = proxyService.registerCompany(company.getPeppolId(), company.getName());
        log.info("Registering company for {} has Peppol active = {}", company.getPeppolId(), registrationResponse.peppolActive());
        company.setRegisteredOnPeppol(registrationResponse.peppolActive());
        companyRepository.save(company);
        return registrationResponse;
    }

    public boolean unregisterCompany(String peppolId) {
        Company company = getByPeppolId(peppolId);
        return unregisterCompany(company);
    }

    public boolean unregisterCompany(Company company) {
        boolean peppolActive = proxyService.unregisterCompany(company.getPeppolId());
        log.info("Unregistering company for {} has Peppol active = {}", company.getPeppolId(), peppolActive);
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

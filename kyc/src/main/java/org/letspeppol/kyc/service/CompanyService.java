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
import org.letspeppol.kyc.service.kbo.KboLookupService;
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
        Company company = new Company(
                peppolId,
                companyResponse.vatNumber(),
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
                company.getPeppolId(),
                company.getVatNumber(),
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

    public void registerCompany(Company company) {
        if (company.isRegisteredOnPeppol()) {
            //TODO : log
            return;
        }
        String token = jwtService.generateInternalToken(company.getPeppolId(), company.isRegisteredOnPeppol());
        company.setRegisteredOnPeppol(proxyService.registerCompany(token, company.getName()));
        companyRepository.save(company);
    }

    public void unregisterCompany(String peppolId) {
        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new KycException(KycErrorCodes.COMPANY_NOT_FOUND));
        String token = jwtService.generateInternalToken(company.getPeppolId(), company.isRegisteredOnPeppol());
        company.setRegisteredOnPeppol(proxyService.unregisterCompany(token));
        companyRepository.save(company);
        companyUnregistrationCounter.increment();
    }
}

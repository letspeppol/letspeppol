package org.letspeppol.app.service;

import org.letspeppol.app.dto.CompanyDto;
import org.letspeppol.app.dto.RegistrationRequest;
import org.letspeppol.app.exception.NotFoundException;
import org.letspeppol.app.mapper.CompanyMapper;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    public void register(RegistrationRequest request) {
        Company account = new Company(
                request.peppolId(),
                request.vatNumber(),
                request.companyName(),
                request.directorName(),
                request.directorEmail(),
                request.city(),
                request.postalCode(),
                request.street(),
                request.houseNumber(),
                "BE"
        );
        companyRepository.save(account);
    }

    public CompanyDto get(String peppolId) {
        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new NotFoundException("Company does not exist"));
        return CompanyMapper.toDto(company);
    }

    public CompanyDto update(CompanyDto companyDto) {
        Company company = companyRepository.findByPeppolId(companyDto.peppolId()).orElseThrow(() -> new NotFoundException("Company does not exist"));
        company.setPaymentAccountName(companyDto.paymentAccountName());
        company.setPaymentTerms(companyDto.paymentTerms());
        company.setIban(companyDto.iban());
        company.getRegisteredOffice().setCity(companyDto.registeredOffice().city());
        company.getRegisteredOffice().setPostalCode(companyDto.registeredOffice().postalCode());
        company.getRegisteredOffice().setStreet(companyDto.registeredOffice().street());
        company.getRegisteredOffice().setHouseNumber(companyDto.registeredOffice().houseNumber());
        companyRepository.save(company);
        return CompanyMapper.toDto(company);
    }

    public void unregister(String peppolId) {
        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new NotFoundException("Company does not exist"));
        company.setRegisteredOnPeppol(false);
        companyRepository.save(company);
    }
}

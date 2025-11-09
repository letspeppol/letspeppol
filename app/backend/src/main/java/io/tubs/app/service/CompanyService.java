package io.tubs.app.service;

import io.tubs.app.dto.CompanyDto;
import io.tubs.app.dto.RegistrationRequest;
import io.tubs.app.exception.NotFoundException;
import io.tubs.app.mapper.CompanyMapper;
import io.tubs.app.model.Company;
import io.tubs.app.repository.CompanyRepository;
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
                request.companyNumber(),
                request.companyName(),
                request.directorName(),
                request.directorEmail(),
                request.city(),
                request.postalCode(),
                request.street(),
                request.houseNumber()
        );
        companyRepository.save(account);
    }

    public CompanyDto get(String companyNumber) {
        Company company = companyRepository.findByCompanyNumber(companyNumber).orElseThrow(() -> new NotFoundException("Company does not exist"));
        return CompanyMapper.toDto(company);
    }

    public CompanyDto update(CompanyDto companyDto) {
        Company company = companyRepository.findByCompanyNumber(companyDto.companyNumber()).orElseThrow(() -> new NotFoundException("Company does not exist"));
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

    public void unregister(String companyNumber) {
        Company company = companyRepository.findByCompanyNumber(companyNumber).orElseThrow(() -> new NotFoundException("Company does not exist"));
        company.setRegisteredOnPeppol(false);
        companyRepository.save(company);
    }
}

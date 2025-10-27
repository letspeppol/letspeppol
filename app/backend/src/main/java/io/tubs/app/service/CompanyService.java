package io.tubs.app.service;

import io.tubs.app.CompanyRepository;
import io.tubs.app.dto.AddressDto;
import io.tubs.app.dto.AppRegistrationRequest;
import io.tubs.app.dto.CompanyDto;
import io.tubs.app.exception.NotFoundException;
import io.tubs.app.model.Address;
import io.tubs.app.model.Company;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    public void register(AppRegistrationRequest request) {
        Company account = new Company(
                request.companyNumber(),
                request.companyName(),
                request.city(),
                request.postalCode(),
                request.street(),
                request.houseNumber(),
                request.directorName(),
                request.directorEmail()
        );
        companyRepository.save(account);
    }

    public CompanyDto get(String companyNumber) {
        Company company = companyRepository.findByCompanyNumber(companyNumber).orElseThrow(() -> new NotFoundException("Company does not exist"));
        return toDto(company);
    }

    public CompanyDto toDto(Company company) {
        return new CompanyDto(
                company.getCompanyNumber(),
                company.getName(),
                company.getSubscriber(),
                company.getSubscriberEmail(),
                company.getPaymentTerms(),
                company.getIban(),
                company.getPaymentAccountName(),
                toDto(company.getRegisteredOffice())
        );
    }

    public AddressDto toDto(Address address) {
        return new AddressDto(
                address.getCity(),
                address.getPostalCode(),
                address.getStreet(),
                address.getHouseNumber()
        );
    }
}

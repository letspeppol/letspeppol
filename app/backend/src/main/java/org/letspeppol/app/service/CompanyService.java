package org.letspeppol.app.service;

import io.micrometer.core.instrument.Counter;
import org.letspeppol.app.dto.CompanyDto;
import org.letspeppol.app.dto.AccountInfo;
import org.letspeppol.app.exception.AppErrorCodes;
import org.letspeppol.app.exception.AppException;
import org.letspeppol.app.exception.NotFoundException;
import org.letspeppol.app.mapper.CompanyMapper;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    @Qualifier("kycWebClient")
    private final WebClient kycWebClient;
    private final Counter companyCreateCounter;

    public Company add(AccountInfo request) {
        companyCreateCounter.increment();
        Company account = new Company(
                request.peppolId(),
                request.vatNumber(),
                request.companyName(),
                request.directorName(),
                request.directorEmail(),
                request.city(),
                request.postalCode(),
                request.street(),
                "BE"
        );
        return companyRepository.save(account);
    }

    public CompanyDto get(String peppolId, Jwt jwt) {
        Optional<Company> optionalCompany = companyRepository.findByPeppolId(peppolId);
        if (optionalCompany.isPresent()) {
            return CompanyMapper.toDto(optionalCompany.get(), JwtUtil.isPeppolActive(jwt));
        }
        try {
            AccountInfo accountInfo = kycWebClient.get()
                    .uri("/sapi/company")
                    .headers(h -> h.setBearerAuth(jwt.getTokenValue())) //.header("Authorization", "Bearer " + TOKEN)
                    .retrieve()
                    .bodyToMono(AccountInfo.class)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Account was not know at KYC"));

            return CompanyMapper.toDto(add(accountInfo), JwtUtil.isPeppolActive(jwt));
        } catch (Exception ex) {
            log.error("Call to KYC /sapi/company failed", ex);
            throw new AppException(AppErrorCodes.KYC_REST_ERROR);
        }
    }

    public CompanyDto update(CompanyDto companyDto, Jwt jwt) {
        Company company = companyRepository.findByPeppolId(companyDto.peppolId()).orElseThrow(() -> new NotFoundException("Company does not exist"));
        company.setPaymentAccountName(companyDto.paymentAccountName());
        company.setPaymentTerms(companyDto.paymentTerms());
        company.setIban(companyDto.iban());
        company.getRegisteredOffice().setCity(companyDto.registeredOffice().city());
        company.getRegisteredOffice().setPostalCode(companyDto.registeredOffice().postalCode());
        company.getRegisteredOffice().setStreet(companyDto.registeredOffice().street());
        companyRepository.save(company);
        return CompanyMapper.toDto(company, JwtUtil.isPeppolActive(jwt));
    }
}

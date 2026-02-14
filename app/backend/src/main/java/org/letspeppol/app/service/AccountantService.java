package org.letspeppol.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.service.spi.ServiceException;
import org.letspeppol.app.dto.accountant.LinkCustomerDto;
import org.letspeppol.app.model.AccountantCompany;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.repository.AccountantCompanyRepository;
import org.letspeppol.app.repository.CompanyRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class AccountantService {

    private final AccountantCompanyRepository accountantCompanyRepository;
    private final CompanyRepository companyRepository;
    private final Map<String, String> cache = new HashMap<>();

    public void linkCustomer(UUID accountantExternalId, String accountantPeppolId, LinkCustomerDto linkCustomerDto) {
        if (!StringUtils.hasText(linkCustomerDto.customerEmail())) {
            throw new ServiceException("Customer email missing");
        }
        Optional<AccountantCompany> existingAccountCompany = accountantCompanyRepository.findByCustomerEmail(linkCustomerDto.customerEmail());
        if (existingAccountCompany.isPresent()) {
            throw new ServiceException("Customer link already exists");
        }
        AccountantCompany accountantCompany = new AccountantCompany(
                accountantExternalId,
                linkCustomerDto.customerPeppolId(),
                linkCustomerDto.customerEmail(),
                linkCustomerDto.customerName()
        );
        accountantCompanyRepository.save(accountantCompany);
        Company company = companyRepository.findByPeppolId(accountantPeppolId).orElseThrow(() -> new ServiceException("Accountant company not found"));
    }



}

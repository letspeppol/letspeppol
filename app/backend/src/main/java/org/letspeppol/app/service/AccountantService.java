package org.letspeppol.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.service.spi.ServiceException;
import org.letspeppol.app.dto.DocumentDto;
import org.letspeppol.app.dto.DocumentFilter;
import org.letspeppol.app.dto.EmailDto;
import org.letspeppol.app.dto.accountant.CustomerDto;
import org.letspeppol.app.dto.accountant.LinkCustomerDto;
import org.letspeppol.app.events.EmailJobCreatedEvent;
import org.letspeppol.app.mapper.DocumentMapper;
import org.letspeppol.app.model.AccountantCustomer;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.EmailJob;
import org.letspeppol.app.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class AccountantService {

    private final AccountantCustomerRepository accountantCustomerRepository;
    private final CompanyRepository companyRepository;
    private final DocumentRepository documentRepository;
    private final EmailTemplateService emailTemplateService;
    private final EmailJobRepository emailJobRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.mail.accountant-link.base-url}")
    private String baseUrl;
    @Value("${notification.mail.from}")
    private String mailFrom;

    public void linkCustomer(UUID accountantExternalId, String accountantPeppolId, LinkCustomerDto linkCustomerDto) {
        if (!StringUtils.hasText(linkCustomerDto.customerEmail())) {
            throw new ServiceException("Customer email missing");
        }
        Optional<AccountantCustomer> existingAccountCompany = accountantCustomerRepository.findByCustomerEmail(linkCustomerDto.customerEmail());
        if (existingAccountCompany.isPresent()) {
            throw new ServiceException("Customer link already exists");
        }
        AccountantCustomer accountantCustomer = new AccountantCustomer(
                accountantExternalId,
                linkCustomerDto.customerPeppolId(),
                linkCustomerDto.customerEmail(),
                linkCustomerDto.customerName()
        );
        accountantCustomerRepository.save(accountantCustomer);
        Company company = companyRepository.findByPeppolId(accountantPeppolId).orElseThrow(() -> new ServiceException("Accountant company not found"));

        String link = baseUrl + accountantCustomer.getToken();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("accountantName", company.getDisplayName());
        placeholders.put("accountantCustomerLink", link);

        String body = emailTemplateService.getTemplateWithPlaceholders(
                EmailJob.Template.ACCOUNTANT_CUSTOMER_LINK,
                placeholders
        );

        EmailDto emailDto = new EmailDto(
                mailFrom,
                linkCustomerDto.customerEmail(), null, null, null,
                "",
                body, null
        );
        try {
            String json = objectMapper.writeValueAsString(emailDto);

            EmailJob emailJob = EmailJob.builder()
                    .toAddress(linkCustomerDto.customerEmail())
                    .payload(json)
                    .build();
            EmailJob saved = emailJobRepository.save(emailJob);
            eventPublisher.publishEvent(new EmailJobCreatedEvent(saved.getId()));
        } catch (JsonProcessingException e) {
            throw new ServiceException("Could you create email json");
        }
    }

    public void confirmLink(String customerPeppolId, String token) {
        Optional<AccountantCustomer> accountantCustomer = accountantCustomerRepository.findByToken(token);
        if (accountantCustomer.isEmpty()) {
            throw new ServiceException("No accountant link request found");
        }
        if (!customerPeppolId.equals(accountantCustomer.get().getCustomerPeppolId())) {
            log.error("Customer {} tried to link to accountant on behalf of {}", customerPeppolId, accountantCustomer.get().getCustomerPeppolId());
            throw new ServiceException("Wrong customer");
        }
        accountantCustomer.get().setVerifiedOn(Instant.now());
        accountantCustomerRepository.save(accountantCustomer.get());
        log.info("Customer {} linked to accountant {}", customerPeppolId, accountantCustomer.get().getAccountantExternalId());
    }

    public List<CustomerDto> getCustomersForAccountant(UUID uid) {
        return accountantCustomerRepository.findCustomerDtosByAccountantExternalId(uid);
    }

    public Page<DocumentDto> getCustomerDocuments(UUID uid, String customerPeppolId, Pageable pageable) {
        if (!accountantCustomerRepository.exists(customerPeppolId, uid)) {
            log.error("Accountant {} tried to fetch documents for customer {} but no link is established", uid, customerPeppolId);
            throw new ServiceException("Wrong customer");
        }
        DocumentFilter filter = new DocumentFilter();
        filter.setOwnerPeppolId(customerPeppolId);
        Sort sort = Sort.by(Sort.Direction.DESC, "issueDate").and(Sort.by(Sort.Direction.DESC, "createdOn"));
        if (pageable == null) {
            pageable = PageRequest.of(0, 20, sort);
        }
        Page<Document> page = documentRepository.findAll(DocumentSpecifications.build(filter), pageable);
        return page.map(DocumentMapper::toDto);
    }

}

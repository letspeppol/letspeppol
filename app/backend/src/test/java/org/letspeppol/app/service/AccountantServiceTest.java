package org.letspeppol.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.service.spi.ServiceException;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.dto.accountant.LinkCustomerDto;
import org.letspeppol.app.model.AccountantCustomer;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.EmailJob;
import org.letspeppol.app.repository.AccountantCustomerRepository;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.DocumentRepository;
import org.letspeppol.app.repository.EmailJobRepository;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountantServiceTest {

    private static final UUID ACCOUNTANT = UUID.randomUUID();
    private static final String ACCOUNTANT_PEPPOL_ID = "0208:0000000009";
    private static final String MAILBOX = "accounts@example.com";

    private final AccountantCustomerRepository accountantCustomerRepository = mock(AccountantCustomerRepository.class);
    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final EmailTemplateService emailTemplateService = mock(EmailTemplateService.class);
    private final EmailJobRepository emailJobRepository = mock(EmailJobRepository.class);
    private final AccountantService service = new AccountantService(
            accountantCustomerRepository,
            companyRepository,
            mock(DocumentRepository.class),
            emailTemplateService,
            emailJobRepository,
            new ObjectMapper(),
            mock(ApplicationEventPublisher.class));

    @Test
    void oneMailboxCanBeLinkedForSeveralCompanies() {
        when(companyRepository.findByPeppolId(ACCOUNTANT_PEPPOL_ID)).thenReturn(Optional.of(mock(Company.class)));
        when(emailTemplateService.getTemplateWithPlaceholders(any(), anyMap())).thenReturn("body");
        when(emailJobRepository.save(any(EmailJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.linkCustomer(ACCOUNTANT, ACCOUNTANT_PEPPOL_ID, new LinkCustomerDto("0208:0000000001", MAILBOX, "Company A"));
        service.linkCustomer(ACCOUNTANT, ACCOUNTANT_PEPPOL_ID, new LinkCustomerDto("0208:0000000002", MAILBOX, "Company B"));

        verify(accountantCustomerRepository, times(2)).save(any(AccountantCustomer.class));
    }

    @Test
    void theSameCompanyCannotBeLinkedTwiceToOneAccountant() {
        when(accountantCustomerRepository.existsByAccountantExternalIdAndCustomerPeppolId(ACCOUNTANT, "0208:0000000001")).thenReturn(true);

        assertThatThrownBy(() -> service.linkCustomer(ACCOUNTANT, ACCOUNTANT_PEPPOL_ID, new LinkCustomerDto("0208:0000000001", MAILBOX, "Company A")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("already exists");
        verify(accountantCustomerRepository, never()).save(any());
    }
}

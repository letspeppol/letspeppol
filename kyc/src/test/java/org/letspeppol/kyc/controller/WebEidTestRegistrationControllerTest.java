package org.letspeppol.kyc.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.EmailVerification;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.service.AccountService;
import org.letspeppol.kyc.service.ActivationService;
import org.letspeppol.kyc.service.CompanyService;
import org.letspeppol.kyc.service.OwnershipService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.letspeppol.kyc.model.AccountType.ADMIN;
import static org.mockito.Mockito.*;

class WebEidTestRegistrationControllerTest {
    private final ActivationService activation = mock(ActivationService.class);
    private final AccountService accounts = mock(AccountService.class);
    private final CompanyService companies = mock(CompanyService.class);
    private final OwnershipService ownerships = mock(OwnershipService.class);
    private final EntityManager entityManager = mock(EntityManager.class);

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            postgres,dev | 127.0.0.1 | false
            postgres,web-eid-testing | 127.0.0.1 | false
            postgres,dev,web-eid-testing,prod | 127.0.0.1 | false
            postgres,dev,web-eid-testing,production | 127.0.0.1 | false
            postgres,dev,web-eid-testing | 0.0.0.0 | false
            postgres,dev,web-eid-testing | 127.0.0.1 | true
            """)
    void onlyExposesBypassWithExplicitLocalTesting(String profiles, String address, boolean enabled) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles.split(","));
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "server.address=" + address);
            context.registerBean(ActivationService.class, () -> activation);
            context.registerBean(AccountService.class, () -> accounts);
            context.registerBean(CompanyService.class, () -> companies);
            context.registerBean(OwnershipService.class, () -> ownerships);
            context.registerBean(EntityManager.class, () -> entityManager);
            context.register(WebEidTestRegistrationController.class);
            context.refresh();
            assertThat(context.getBeansOfType(WebEidTestRegistrationController.class)).hasSize(enabled ? 1 : 0);
        }
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    void usesTheRegistrationEmailAndCompanyWithoutResettingAnExistingPassword(boolean existing) {
        var email = "registration@example.test";
        var peppolId = "0208:0123456789";
        var company = new Company(peppolId, "0123456789", "BE0123456789", "Company");
        var director = new Director("Selected Director", company);
        director.setId(7L);
        company.addDirector(director);
        var verification = new EmailVerification(null, ADMIN, email, peppolId, "test-link", Instant.now().plusSeconds(60));
        verification.setId(42L);
        var account = new Account();
        account.setVerified(existing);
        var query = mock(Query.class, RETURNS_SELF);
        when(entityManager.createQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);
        when(activation.getValidTokenInformation("test-link")).thenReturn(verification);
        when(companies.getByPeppolId(peppolId)).thenReturn(company);
        when(accounts.findByEmail(email)).thenReturn(existing ? Optional.of(account) : Optional.empty());
        when(accounts.createPendingAccount(email, director.getName())).thenReturn(account);

        var controller = new WebEidTestRegistrationController(activation, accounts, companies, ownerships, entityManager);
        assertThat(controller.complete(new WebEidTestRegistrationController.Request("test-link", "local-password", 7L))
                .getStatusCode().value()).isEqualTo(204);
        verify(query).setParameter("id", 42L);
        verify(query).executeUpdate();
        verify(ownerships).ensureAdminOwnership(account, company);
        verify(accounts, times(existing ? 0 : 1)).createPendingAccount(email, director.getName());
        verify(accounts, times(existing ? 0 : 1)).updatePassword(account, "local-password");
        verify(accounts, times(existing ? 0 : 1)).verify(account);
    }
}

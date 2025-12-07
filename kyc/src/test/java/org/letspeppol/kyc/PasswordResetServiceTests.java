package org.letspeppol.kyc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.PasswordResetToken;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.repository.AccountRepository;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.PasswordResetTokenRepository;
import org.letspeppol.kyc.service.PasswordResetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PasswordResetServiceTests {

    @Autowired
    PasswordResetService passwordResetService;
    @Autowired
    PasswordResetTokenRepository tokenRepository;
    @Autowired
    AccountRepository accountRepository;
    @Autowired
    CompanyRepository companyRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private Account account;

    @BeforeEach
    void setup() {
        tokenRepository.deleteAll();
        accountRepository.deleteAll();
        companyRepository.deleteAll();
        Company company = new Company("0208:0123456789", "BE0123456789", "TestCo");
        company.setAddress("City", "1000", "Street 1");
        companyRepository.save(company);
        account = Account.builder()
                .name("John Doe")
                .email("user@example.com")
                .company(company)
                .passwordHash(passwordEncoder.encode("initialPassword123"))
                .build();
        accountRepository.save(account);
    }

    @Test
    void requestResetCreatesTokenForExistingAccount() {
        passwordResetService.requestReset(account.getEmail());
        assertThat(tokenRepository.findAll()).hasSize(1);
        PasswordResetToken token = tokenRepository.findAll().get(0);
        assertThat(token.getAccount().getId()).isEqualTo(account.getId());
    }

    @Test
    void requestResetDoesNothingForUnknownAccount() {
        passwordResetService.requestReset("missing@example.com");
        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    void resetPasswordHappyPathMarksTokenUsedAndChangesPassword() {
        passwordResetService.requestReset(account.getEmail());
        PasswordResetToken token = tokenRepository.findAll().get(0);
        passwordResetService.resetPassword(token.getToken(), "newStrongPassword!");
        PasswordResetToken updated = tokenRepository.findById(token.getId()).orElseThrow();
        assertThat(updated.getUsedOn()).isNotNull();
        Account refreshed = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("newStrongPassword!", refreshed.getPasswordHash())).isTrue();
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        passwordResetService.requestReset(account.getEmail());
        PasswordResetToken token = tokenRepository.findAll().get(0);
        token.setExpiresOn(Instant.now().minus(2, ChronoUnit.HOURS));
        tokenRepository.save(token);
        assertThatThrownBy(() -> passwordResetService.resetPassword(token.getToken(), "anotherPassword123"))
                .isInstanceOf(KycException.class)
                .hasMessage(KycErrorCodes.PASSWORD_RESET_TOKEN_EXPIRED);
    }

    @Test
    void resetPasswordRejectsUsedToken() {
        passwordResetService.requestReset(account.getEmail());
        PasswordResetToken token = tokenRepository.findAll().get(0);
        passwordResetService.resetPassword(token.getToken(), "newStrongPassword!");
        assertThatThrownBy(() -> passwordResetService.resetPassword(token.getToken(), "secondTryPassword"))
                .isInstanceOf(KycException.class)
                .hasMessage(KycErrorCodes.PASSWORD_RESET_TOKEN_ALREADY_USED);
    }

    @Test
    void resetPasswordRejectsWeakPassword() {
        passwordResetService.requestReset(account.getEmail());
        PasswordResetToken token = tokenRepository.findAll().get(0);
        assertThatThrownBy(() -> passwordResetService.resetPassword(token.getToken(), "123"))
                .isInstanceOf(KycException.class)
                .hasMessage(KycErrorCodes.INVALID_PASSWORD);
    }

    @Test
    void purgeExpiredRemovesExpiredUnusedTokens() {
        passwordResetService.requestReset(account.getEmail());
        PasswordResetToken token1 = tokenRepository.findAll().get(0);
        token1.setExpiresOn(Instant.now().minus(90, ChronoUnit.MINUTES));
        tokenRepository.save(token1);
        passwordResetService.requestReset(account.getEmail());
        long purged = passwordResetService.purgeExpired();
        assertThat(purged).isEqualTo(1L);
        assertThat(tokenRepository.findAll()).hasSize(1);
    }
}

package io.tubs.kyc;

import io.tubs.kyc.exception.KycErrorCodes;
import io.tubs.kyc.exception.KycException;
import io.tubs.kyc.model.PasswordResetToken;
import io.tubs.kyc.model.User;
import io.tubs.kyc.model.kbo.Company;
import io.tubs.kyc.repository.PasswordResetTokenRepository;
import io.tubs.kyc.repository.UserRepository;
import io.tubs.kyc.repository.CompanyRepository;
import io.tubs.kyc.service.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class PasswordResetServiceTests {

    @Autowired
    PasswordResetService passwordResetService;
    @Autowired
    PasswordResetTokenRepository tokenRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    CompanyRepository companyRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setup() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
        Company company = new Company("BE0123456789", "TestCo", "City", "1000", "Street", "1");
        companyRepository.save(company);
        user = User.builder()
                .email("user@example.com")
                .company(company)
                .passwordHash(passwordEncoder.encode("initialPassword123"))
                .build();
        userRepository.save(user);
    }

    @Test
    void requestResetCreatesTokenForExistingUser() {
        passwordResetService.requestReset(user.getEmail());
        assertThat(tokenRepository.findAll()).hasSize(1);
        PasswordResetToken token = tokenRepository.findAll().get(0);
        assertThat(token.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void requestResetDoesNothingForUnknownUser() {
        passwordResetService.requestReset("missing@example.com");
        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    void resetPasswordHappyPathMarksTokenUsedAndChangesPassword() {
        passwordResetService.requestReset(user.getEmail());
        PasswordResetToken token = tokenRepository.findAll().get(0);
        passwordResetService.resetPassword(token.getToken(), "newStrongPassword!");
        PasswordResetToken updated = tokenRepository.findById(token.getId()).orElseThrow();
        assertThat(updated.getUsedAt()).isNotNull();
        User refreshed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("newStrongPassword!", refreshed.getPasswordHash())).isTrue();
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        passwordResetService.requestReset(user.getEmail());
        PasswordResetToken token = tokenRepository.findAll().get(0);
        token.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));
        tokenRepository.save(token);
        assertThatThrownBy(() -> passwordResetService.resetPassword(token.getToken(), "anotherPassword123"))
                .isInstanceOf(KycException.class)
                .hasMessage(KycErrorCodes.PASSWORD_RESET_TOKEN_EXPIRED);
    }

    @Test
    void resetPasswordRejectsUsedToken() {
        passwordResetService.requestReset(user.getEmail());
        PasswordResetToken token = tokenRepository.findAll().get(0);
        passwordResetService.resetPassword(token.getToken(), "newStrongPassword!");
        assertThatThrownBy(() -> passwordResetService.resetPassword(token.getToken(), "secondTryPassword"))
                .isInstanceOf(KycException.class)
                .hasMessage(KycErrorCodes.PASSWORD_RESET_TOKEN_ALREADY_USED);
    }

    @Test
    void resetPasswordRejectsWeakPassword() {
        passwordResetService.requestReset(user.getEmail());
        PasswordResetToken token = tokenRepository.findAll().get(0);
        assertThatThrownBy(() -> passwordResetService.resetPassword(token.getToken(), "123"))
                .isInstanceOf(KycException.class)
                .hasMessage(KycErrorCodes.INVALID_PASSWORD);
    }

    @Test
    void purgeExpiredRemovesExpiredUnusedTokens() {
        passwordResetService.requestReset(user.getEmail());
        PasswordResetToken token1 = tokenRepository.findAll().get(0);
        token1.setExpiresAt(Instant.now().minus(90, ChronoUnit.MINUTES));
        tokenRepository.save(token1);
        passwordResetService.requestReset(user.getEmail());
        long purged = passwordResetService.purgeExpired();
        assertThat(purged).isEqualTo(1L);
        assertThat(tokenRepository.findAll()).hasSize(1);
    }
}

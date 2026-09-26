package org.letspeppol.kyc;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.EmailVerification;
import org.letspeppol.kyc.repository.EmailVerificationRepository;
import org.letspeppol.kyc.service.ActivationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ActivationCleanupJobTests {

    @Autowired
    ActivationService activationService;
    @Autowired
    EmailVerificationRepository repository;

    @Test
    void purgeExpiredRemovesOnlyExpiredUnverified() {
        // expired & unverified (should be removed)
        repository.save(new EmailVerification(null, AccountType.ADMIN, "a@example.com", "BE0123456789", "tok-expired-unverified", Instant.now().minus(3, ChronoUnit.HOURS)));
        // future & unverified (should stay)
        repository.save(new EmailVerification(null, AccountType.ADMIN,"b@example.com", "BE0123456789", "tok-future-unverified", Instant.now().plus(1, ChronoUnit.HOURS)));
        // expired & verified (should stay)
        EmailVerification verifiedExpired = new EmailVerification(null, AccountType.ADMIN,"c@example.com", "BE0123456789", "tok-expired-verified", Instant.now().minus(4, ChronoUnit.HOURS));
        verifiedExpired.setVerified(true);
        repository.save(verifiedExpired);

        long purged = activationService.purgeExpired();
        assertThat(purged).isEqualTo(1L);
        assertThat(repository.findByToken("tok-expired-unverified")).isEmpty();
        assertThat(repository.findByToken("tok-future-unverified")).isPresent();
        assertThat(repository.findByToken("tok-expired-verified")).isPresent();
    }
}


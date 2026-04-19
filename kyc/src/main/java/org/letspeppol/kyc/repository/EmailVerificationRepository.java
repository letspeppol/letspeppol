package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findByToken(String token);
    Optional<EmailVerification> findTopByPeppolIdOrderByCreatedOnDesc(String peppolId);
    Optional<EmailVerification> findFirstByEmailIgnoreCaseAndPeppolIdAndVerifiedFalseAndExpiresOnAfterOrderByCreatedOnDesc(String email, String peppolId, Instant cutoff);
    Optional<EmailVerification> findFirstByEmailAndPeppolIdAndTypeAndVerifiedFalseOrderByCreatedOnDesc(String email, String peppolId, org.letspeppol.kyc.model.AccountType type);
    List<EmailVerification> findByVerifiedFalseAndExpiresOnBefore(Instant cutoff);
}

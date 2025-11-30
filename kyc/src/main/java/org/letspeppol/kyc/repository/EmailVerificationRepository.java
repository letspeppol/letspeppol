package org.letspeppol.kyc.repository;

import aj.org.objectweb.asm.commons.Remapper;
import org.letspeppol.kyc.model.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
    Optional<EmailVerification> findByToken(String token);
    Optional<EmailVerification> findTopByPeppolIdOrderByCreatedOnDesc(String peppolId);
    List<EmailVerification> findByVerifiedFalseAndExpiresOnBefore(Instant cutoff);
}

package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    List<PasswordResetToken> findByUsedAtIsNullAndExpiresAtBefore(Instant time);
}


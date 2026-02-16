package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.DirectorIdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountIdentityVerificationRepository extends JpaRepository<DirectorIdentityVerification, Long> {
    List<DirectorIdentityVerification> findByAccountId(Long accountId);
}


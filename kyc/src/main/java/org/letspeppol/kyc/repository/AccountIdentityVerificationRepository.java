package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.DirectorIdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountIdentityVerificationRepository extends JpaRepository<DirectorIdentityVerification, Long> {
    List<DirectorIdentityVerification> findByAccountId(Long accountId);
    boolean existsByAccountId(Long accountId);
    boolean existsByAccountIdAndDirectorCompanyPeppolId(Long accountId, String peppolId);
    Optional<DirectorIdentityVerification> findTopByAccountIdOrderByCreatedOnDesc(Long accountId);
}


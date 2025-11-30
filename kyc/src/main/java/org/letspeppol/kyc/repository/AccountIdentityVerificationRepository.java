package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.AccountIdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountIdentityVerificationRepository extends JpaRepository<AccountIdentityVerification, Long> {
    List<AccountIdentityVerification> findByAccountId(Long accountId);
}


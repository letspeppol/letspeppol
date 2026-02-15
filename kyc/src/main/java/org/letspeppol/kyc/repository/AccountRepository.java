package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);

    Optional<Account> findByExternalId(UUID externalId);
    boolean existsByTypeAndCompanyPeppolId(AccountType accountType, String peppolId);
    Optional<Account> findFirstByTypeAndCompanyPeppolId(AccountType accountType, String peppolId);
}


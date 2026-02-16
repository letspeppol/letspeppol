package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnershipRepository extends JpaRepository<Ownership, Long> {
    Optional<Ownership> findFirstByAccountExternalIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(UUID externalId, String peppolId, AccountType type);
    List<Ownership> findByCompanyPeppolIdOrderByCreatedOnAsc(String peppolId);
    boolean existsByTypeAndCompanyPeppolId(AccountType type, String peppolId);
}

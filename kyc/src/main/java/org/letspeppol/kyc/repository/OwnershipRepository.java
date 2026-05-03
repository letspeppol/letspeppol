package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnershipRepository extends JpaRepository<Ownership, Long> {
    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByAccountIdOrderByLastUsedDesc(Long accountId);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByAccountIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(Long accountId, String peppolId, AccountType type);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByAccountExternalIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(UUID externalId, String peppolId, AccountType type);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByCompanyPeppolIdAndTypeOrderByLastUsedDesc(String peppolId, AccountType type);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByAccountEmailAndCompanyPeppolIdAndTypeOrderByCreatedOnDesc(String email, String peppolId, AccountType type);

    Optional<Ownership> findFirstByAccountIdAndCompanyIdAndType(Long accountId, Long companyId, AccountType type);

    @EntityGraph(attributePaths = {"account", "company"})
    List<Ownership> findByCompanyPeppolIdOrderByCreatedOnAsc(String peppolId);

    boolean existsByTypeAndCompanyPeppolId(AccountType type, String peppolId);
}

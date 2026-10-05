package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.model.OwnershipStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OwnershipRepository extends JpaRepository<Ownership, Long> {
    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByAccountIdOrderByLastUsedDesc(Long accountId);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByAccountIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(Long accountId, String peppolId, AccountType type);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByAccountIdAndCompanyPeppolIdOrderByLastUsedDesc(Long accountId, String peppolId);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByAccountExternalIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(UUID externalId, String peppolId, AccountType type);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByCompanyPeppolIdAndTypeOrderByLastUsedDesc(String peppolId, AccountType type);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByAccountEmailAndCompanyPeppolIdAndTypeOrderByCreatedOnDesc(String email, String peppolId, AccountType type);

    Optional<Ownership> findFirstByAccountIdAndCompanyIdAndType(Long accountId, Long companyId, AccountType type);

    @EntityGraph(attributePaths = {"account", "company"})
    List<Ownership> findByCompanyPeppolIdOrderByCreatedOnAsc(String peppolId);

    @EntityGraph(attributePaths = {"account", "company"})
    List<Ownership> findByAccountExternalIdOrderByCreatedOnAsc(UUID externalId);

    boolean existsByTypeAndCompanyPeppolId(AccountType type, String peppolId);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByAccountIdAndStatusOrderByLastUsedDesc(Long accountId, OwnershipStatus status);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByAccountIdAndCompanyPeppolIdAndTypeAndStatusOrderByLastUsedDesc(Long accountId, String peppolId, AccountType type, OwnershipStatus status);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByAccountIdAndCompanyPeppolIdAndStatusOrderByLastUsedDesc(Long accountId, String peppolId, OwnershipStatus status);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findFirstByAccountExternalIdAndCompanyPeppolIdAndTypeAndStatusOrderByLastUsedDesc(UUID externalId, String peppolId, AccountType type, OwnershipStatus status);

    @EntityGraph(attributePaths = {"account", "company"})
    List<Ownership> findByAccountExternalIdAndStatusOrderByCreatedOnAsc(UUID externalId, OwnershipStatus status);

    @EntityGraph(attributePaths = {"account", "company"})
    List<Ownership> findByCompanyIdAndTypeInOrderByCreatedOnAsc(Long companyId, Collection<AccountType> types);

    @EntityGraph(attributePaths = {"account", "company"})
    Optional<Ownership> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByAccountIdAndCompanyIdAndTypeIn(Long accountId, Long companyId, Collection<AccountType> types);
}

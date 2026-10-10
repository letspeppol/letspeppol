package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.OwnershipInvitation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OwnershipInvitationRepository extends JpaRepository<OwnershipInvitation, Long> {
    @EntityGraph(attributePaths = {"ownership", "ownership.account", "ownership.company"})
    Optional<OwnershipInvitation> findByToken(String token);

    Optional<OwnershipInvitation> findByOwnershipId(Long ownershipId);

    List<OwnershipInvitation> findByOwnershipCompanyId(Long companyId);
}

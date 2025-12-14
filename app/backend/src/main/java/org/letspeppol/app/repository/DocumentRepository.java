package org.letspeppol.app.repository;

import org.letspeppol.app.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

//    @Query("SELECT document FROM Document document WHERE document.company.peppolId = :peppolId ORDER BY document.createdOn DESC")
    Collection<Document> findAllByOwnerPeppolId(String peppolId);

    @Query("SELECT document.id FROM Document document WHERE document.company.peppolId = :peppolId AND document.proxyOn is not null AND document.processedOn is null AND document.draftedOn is null AND ( document.scheduledOn is null OR document.scheduledOn < :maximalScheduledOn )")
    List<UUID> findIdsWithPossibleStatusUpdatesOnProxy(@Param("peppolId") String peppolId, @Param("maximalScheduledOn") Instant maximalScheduledOn);

//    @Modifying
//    @Query("DELETE FROM Document document WHERE document.id = :id AND document.company.peppolId = :peppolId")
    void deleteByIdAndOwnerPeppolId(UUID id, String peppolId);
}

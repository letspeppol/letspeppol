package org.letspeppol.app.repository;

import org.letspeppol.app.dto.TotalsDto;
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

    @Query(value = """
    SELECT
      COALESCE(SUM(CASE WHEN document.direction = 'INCOMING' AND document.paid_on IS NULL THEN document.amount ELSE 0 END), 0) AS totalPayableOpen,
      COALESCE(SUM(CASE WHEN document.direction = 'INCOMING' AND document.paid_on IS NULL AND due_date < NOW() THEN document.amount ELSE 0 END), 0) AS totalPayableOverdue,
      COALESCE(SUM(CASE WHEN document.direction = 'INCOMING' AND EXTRACT(YEAR FROM document.issue_date) = EXTRACT(YEAR FROM NOW()) THEN document.amount ELSE 0 END), 0) AS totalPayableThisYear,
      COALESCE(SUM(CASE WHEN document.direction = 'OUTGOING' AND document.paid_on IS NULL THEN amount ELSE 0 END), 0) AS totalReceivableOpen,
      COALESCE(SUM(CASE WHEN document.direction = 'OUTGOING' AND document.paid_on IS NULL AND due_date < NOW() THEN document.amount ELSE 0 END), 0) AS totalReceivableOverdue,
      COALESCE(SUM(CASE WHEN document.direction = 'OUTGOING' AND EXTRACT(YEAR FROM document.issue_date) = EXTRACT(YEAR FROM NOW()) THEN document.amount ELSE 0 END), 0) AS totalReceivableThisYear
    FROM Document document
    WHERE owner_peppol_id = :ownerPeppolId
    """, nativeQuery = true)
    TotalsDto totalsByOwner(@Param("ownerPeppolId") String ownerPeppolId);

//    @Query("SELECT count(document) FROM Document document WHERE document.processedOn IS NOT NULL")
    long countByProcessedOnIsNotNull();

    long countByProcessedOnIsNotNullAndIssueDateGreaterThanEqualAndIssueDateLessThan(Instant startInclusive, Instant endExclusive);

    @Query(value = """
    SELECT COALESCE(MAX(day_count), 0) AS max_daily_total
    FROM (
      SELECT date_trunc('day', issue_date) AS day, COUNT(*) AS day_count
      FROM app.document
      WHERE issue_date >= :startInclusive AND issue_date <  :endExclusive
      GROUP BY 1
    ) daily_counts
    """, nativeQuery = true)
    long maxDailyTotal(@Param("startInclusive") Instant startInclusive, @Param("endExclusive") Instant endExclusive);

//    @Modifying
//    @Query("DELETE FROM Document document WHERE document.id = :id AND document.company.peppolId = :peppolId")
    void deleteByIdAndOwnerPeppolId(UUID id, String peppolId);
}

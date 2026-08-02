package org.letspeppol.app.repository;

import org.letspeppol.app.dto.TotalsRow;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.DocumentType;
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

    @Query("""
        SELECT COUNT(document) > 0 FROM Document document
        WHERE document.invoiceReference = :invoiceReference AND document.company.peppolId = :ownerPeppolId and document.type = :type
        AND document.draftedOn IS NULL AND document.proxyOn IS NOT NULL AND document.direction = 'OUTGOING' AND document.processedStatus IS NULL
        """)
    boolean existsByInvoiceReferenceAndTypeAndOwnerPeppolId(String invoiceReference, DocumentType type, String ownerPeppolId);

    // Errored documents (processed_status IS NOT NULL) are excluded from the money totals and counted separately as erroredUnseenCount.
    @Query(value = """
    SELECT
      COALESCE(SUM(CASE WHEN direction = 'INCOMING' AND processed_status IS NULL AND paid_on IS NULL THEN signed_incl END), 0) AS totalPayableOpenInclVat,
      COALESCE(SUM(CASE WHEN direction = 'INCOMING' AND processed_status IS NULL AND paid_on IS NULL AND due_date < NOW() THEN signed_incl END), 0) AS totalPayableOverdueInclVat,
      COALESCE(SUM(CASE WHEN direction = 'INCOMING' AND processed_status IS NULL AND issue_date >= date_trunc('year', NOW()) AND issue_date < date_trunc('year', NOW()) + INTERVAL '1 year' THEN signed_incl END), 0) AS totalPayableThisYearInclVat,
      COALESCE(SUM(CASE WHEN direction = 'OUTGOING' AND processed_status IS NULL AND paid_on IS NULL THEN signed_incl END), 0) AS totalReceivableOpenInclVat,
      COALESCE(SUM(CASE WHEN direction = 'OUTGOING' AND processed_status IS NULL AND paid_on IS NULL AND due_date < NOW() THEN signed_incl END), 0) AS totalReceivableOverdueInclVat,
      COALESCE(SUM(CASE WHEN direction = 'OUTGOING' AND processed_status IS NULL AND issue_date >= date_trunc('year', NOW()) AND issue_date < date_trunc('year', NOW()) + INTERVAL '1 year' THEN signed_incl END), 0) AS totalReceivableThisYearInclVat,
      COALESCE(SUM(CASE WHEN direction = 'INCOMING' AND processed_status IS NULL AND paid_on IS NULL THEN signed_excl END), 0) AS totalPayableOpenExclVat,
      COALESCE(SUM(CASE WHEN direction = 'INCOMING' AND processed_status IS NULL AND paid_on IS NULL AND due_date < NOW() THEN signed_excl END), 0) AS totalPayableOverdueExclVat,
      COALESCE(SUM(CASE WHEN direction = 'INCOMING' AND processed_status IS NULL AND issue_date >= date_trunc('year', NOW()) AND issue_date < date_trunc('year', NOW()) + INTERVAL '1 year' THEN signed_excl END), 0) AS totalPayableThisYearExclVat,
      COALESCE(SUM(CASE WHEN direction = 'OUTGOING' AND processed_status IS NULL AND paid_on IS NULL THEN signed_excl END), 0) AS totalReceivableOpenExclVat,
      COALESCE(SUM(CASE WHEN direction = 'OUTGOING' AND processed_status IS NULL AND paid_on IS NULL AND due_date < NOW() THEN signed_excl END), 0) AS totalReceivableOverdueExclVat,
      COALESCE(SUM(CASE WHEN direction = 'OUTGOING' AND processed_status IS NULL AND issue_date >= date_trunc('year', NOW()) AND issue_date < date_trunc('year', NOW()) + INTERVAL '1 year' THEN signed_excl END), 0) AS totalReceivableThisYearExclVat,
      COALESCE(SUM(CASE WHEN direction = 'OUTGOING' AND processed_status IS NOT NULL AND error_seen_on IS NULL THEN 1 ELSE 0 END), 0) AS erroredUnseenCount
    FROM (
      SELECT
        direction,
        paid_on,
        due_date,
        issue_date,
        processed_status,
        error_seen_on,
        CASE WHEN type = 'CREDIT_NOTE' THEN -amount_incl_vat ELSE amount_incl_vat END AS signed_incl,
        CASE WHEN type = 'CREDIT_NOTE' THEN -amount_excl_vat ELSE amount_excl_vat END AS signed_excl
      FROM app.document
      WHERE owner_peppol_id = :ownerPeppolId AND drafted_on IS NULL
    ) d
    """, nativeQuery = true)
    TotalsRow totalsByOwner(@Param("ownerPeppolId") String ownerPeppolId);

//    @Query("SELECT count(document) FROM Document document WHERE document.processedOn IS NOT NULL")
    long countByProcessedOnIsNotNull();

    long countByProcessedOnIsNotNullAndIssueDateGreaterThanEqualAndIssueDateLessThan(Instant startInclusive, Instant endExclusive);

    @Query(value = """
    SELECT COALESCE(MAX(day_count), 0) AS max_daily_total
    FROM (
      SELECT date_trunc('day', issue_date) AS day, COUNT(*) AS day_count
      FROM app.document
      WHERE processed_on IS NOT NULL AND issue_date >= :startInclusive AND issue_date <  :endExclusive
      GROUP BY 1
    ) daily_counts
    """, nativeQuery = true)
    long maxDailyTotal(@Param("startInclusive") Instant startInclusive, @Param("endExclusive") Instant endExclusive);

//    @Modifying
//    @Query("DELETE FROM Document document WHERE document.id = :id AND document.company.peppolId = :peppolId")
    void deleteByIdAndOwnerPeppolId(UUID id, String peppolId);

}

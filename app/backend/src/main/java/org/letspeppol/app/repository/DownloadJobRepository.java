package org.letspeppol.app.repository;

import jakarta.persistence.LockModeType;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.DownloadJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DownloadJobRepository extends JpaRepository<DownloadJob, Long> {

    List<DownloadJob> findAllByCompanyPeppolIdOrderByCreatedOnDesc(String peppolId);

    Optional<DownloadJob> findByIdAndCompanyPeppolId(Long id, String peppolId);

    long countByCompanyAndStatusIn(Company company, Collection<DownloadJob.Status> statuses);

    List<DownloadJob> findAllByStatusAndExpiresOnLessThanEqual(DownloadJob.Status status, Instant expiresOn);

    List<DownloadJob> findAllByStatus(DownloadJob.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM DownloadJob job WHERE job.id = :id")
    Optional<DownloadJob> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM DownloadJob job WHERE job.id = :id AND job.company.peppolId = :peppolId")
    Optional<DownloadJob> findByIdAndPeppolIdForUpdate(@Param("id") Long id, @Param("peppolId") String peppolId);

    @Modifying
    @Query("""
            UPDATE DownloadJob job
               SET job.status = :pendingStatus, job.processingStartedOn = NULL
             WHERE job.status = :processingStatus AND job.processingStartedOn < :staleBefore
            """)
    int recoverStaleProcessing(@Param("staleBefore") Instant staleBefore,
                               @Param("pendingStatus") DownloadJob.Status pendingStatus,
                               @Param("processingStatus") DownloadJob.Status processingStatus);
}

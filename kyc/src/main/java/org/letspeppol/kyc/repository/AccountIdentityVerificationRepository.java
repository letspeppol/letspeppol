package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.DirectorIdentityVerification;
import org.letspeppol.kyc.model.ReviewStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountIdentityVerificationRepository extends JpaRepository<DirectorIdentityVerification, Long> {
    List<DirectorIdentityVerification> findByAccountId(Long accountId);
    boolean existsByAccountId(Long accountId);
    boolean existsByAccountIdAndDirectorCompanyPeppolId(Long accountId, String peppolId);
    boolean existsByAccountIdAndReviewStatus(Long accountId, ReviewStatus reviewStatus);
    boolean existsByAccountIdAndDirectorCompanyPeppolIdAndReviewStatus(Long accountId, String peppolId, ReviewStatus reviewStatus);
    Optional<DirectorIdentityVerification> findTopByAccountIdOrderByCreatedOnDesc(Long accountId);
    List<DirectorIdentityVerification> findByAccountIdAndDirectorCompanyIdAndReviewStatusIn(Long accountId, Long companyId, Collection<ReviewStatus> reviewStatuses);

    @Query("select v.director.company.id from DirectorIdentityVerification v where v.id = :id")
    Optional<Long> findCompanyIdById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"account", "director", "director.company", "reviewedBy"})
    List<DirectorIdentityVerification> findTop100ByReviewStatusOrderByCreatedOnAsc(ReviewStatus reviewStatus);

    @EntityGraph(attributePaths = {"account", "director", "director.company", "reviewedBy"})
    List<DirectorIdentityVerification> findTop100ByReviewStatusOrderByReviewedOnDesc(ReviewStatus reviewStatus);

    @EntityGraph(attributePaths = {"account", "director", "director.company", "reviewedBy"})
    Optional<DirectorIdentityVerification> findWithDetailsById(Long id);
}

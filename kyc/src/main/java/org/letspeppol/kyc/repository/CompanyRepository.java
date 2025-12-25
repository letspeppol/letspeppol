package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.kbo.Company;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByPeppolId(String peppolId);

    @EntityGraph(attributePaths = "directors")
    Optional<Company> findWithDirectorsByPeppolId(String peppolId);

    Optional<Company> findByBusinessUnitAndHasKboAddressFalse(String businessUnit);

    @Query("""
            select (count(d) > 0)
            from Company c
            join c.directors d
            where c.peppolId = :peppolId
              and d.registered = true
            """)
    boolean existsRegisteredDirectorForPeppolId(@Param("peppolId") String peppolId);

    @Query("""
            SELECT c FROM Company c WHERE
            (:vatNumber IS NULL OR c.vatNumber = :vatNumber) AND
            (:peppolId IS NULL OR c.peppolId = :peppolId) AND
            (:name IS NULL OR LOWER(c.name) LIKE CONCAT(LOWER(:name), '%'))
            """)
    List<Company> search(@Param("vatNumber") String vatNumber,
                         @Param("peppolId") String peppolId,
                         @Param("name") String name,
                         Pageable pageable);
}

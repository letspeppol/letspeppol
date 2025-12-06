package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.kbo.Company;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByPeppolId(String peppolId);

    @EntityGraph(attributePaths = "directors")
    Optional<Company> findWithDirectorsByPeppolId(String peppolId);

    @Query("""
            select (count(d) > 0)
            from Company c
            join c.directors d
            where c.peppolId = :peppolId
              and d.registered = true
            """)
    boolean existsRegisteredDirectorForPeppolId(@Param("peppolId") String peppolId);
}

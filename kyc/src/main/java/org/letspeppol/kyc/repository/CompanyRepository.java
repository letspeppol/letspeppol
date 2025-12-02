package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.kbo.Company;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByPeppolId(String peppolId);

    @EntityGraph(attributePaths = "directors")
    Optional<Company> findWithDirectorsByPeppolId(String peppolId);
}

package org.letspeppol.app.repository;

import org.letspeppol.app.model.Company;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByPeppolId(String peppolId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT company FROM Company company WHERE company.peppolId = :peppolId")
    Optional<Company> findByPeppolIdForUpdate(@Param("peppolId") String peppolId);

}

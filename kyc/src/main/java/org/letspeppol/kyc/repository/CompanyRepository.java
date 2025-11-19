package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.kbo.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByCompanyNumber(String companyNumber);
}


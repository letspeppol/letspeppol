package org.letspeppol.app.repository;

import org.letspeppol.app.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByCompanyNumber(String companyNumber);

}
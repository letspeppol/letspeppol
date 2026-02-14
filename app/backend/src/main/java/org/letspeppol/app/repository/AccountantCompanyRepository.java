package org.letspeppol.app.repository;

import org.letspeppol.app.model.AccountantCompany;
import org.letspeppol.app.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountantCompanyRepository extends JpaRepository<AccountantCompany, Long> {

    Optional<AccountantCompany> findByCustomerEmail(String customerEmail);

}

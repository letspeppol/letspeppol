package org.letspeppol.app.repository;

import org.letspeppol.app.model.AccountantCustomer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountantCustomerRepository extends JpaRepository<AccountantCustomer, Long> {

    Optional<AccountantCustomer> findByCustomerEmail(String customerEmail);

    Optional<AccountantCustomer> findByToken(String customerPeppolId);
}

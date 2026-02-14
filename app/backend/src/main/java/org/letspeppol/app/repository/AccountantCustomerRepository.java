package org.letspeppol.app.repository;

import org.letspeppol.app.dto.accountant.CustomerDto;
import org.letspeppol.app.model.AccountantCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountantCustomerRepository extends JpaRepository<AccountantCustomer, Long> {

    Optional<AccountantCustomer> findByCustomerEmail(String customerEmail);

    Optional<AccountantCustomer> findByToken(String customerPeppolId);

    @Query("""
            SELECT new org.letspeppol.app.dto.accountant.CustomerDto(
                ac.id,
                ac.customerPeppolId,
                ac.customerEmail,
                CASE WHEN ac.customerName IS NOT NULL AND ac.customerName <> ''
                     THEN ac.customerName
                     ELSE COALESCE(NULLIF(c.displayName, ''), c.name)
                END,
                ac.invitedOn,
                ac.verifiedOn,
                ac.lastDownloadCreatedOn,
                ac.lastDownloadIssuedOn
            )
            FROM AccountantCustomer ac
            LEFT JOIN Company c ON c.peppolId = ac.customerPeppolId
            WHERE ac.accountantExternalId = :accountantExternalId
            AND ac.verifiedOn IS NOT NULL
            """)
    List<CustomerDto> findCustomerDtosByAccountantExternalId(@Param("accountantExternalId") UUID accountantExternalId);

    @Query("""
        SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END
        FROM AccountantCustomer e
        WHERE e.customerPeppolId = :customerPeppolId
        AND e.accountantExternalId = :accountantExternalId
        AND e.verifiedOn IS NOT NULL
        """)
    boolean exists(String customerPeppolId, UUID accountantExternalId);
}

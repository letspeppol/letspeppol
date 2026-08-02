package org.letspeppol.app.repository;

import org.letspeppol.app.model.Partner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PartnerRepository extends JpaRepository<Partner, Long> {
    @Query("SELECT partner FROM Partner partner WHERE partner.company.peppolId = :peppolId ORDER BY partner.name DESC")
    List<Partner> findByOwningPeppolId(String peppolId);

    @Query("SELECT partner FROM Partner partner WHERE partner.id = :id AND partner.company.peppolId = :peppolId")
    Optional<Partner> findByIdAndCompanyPeppolId(Long id, String peppolId);

    @Query("""
            SELECT partner FROM Partner partner
            WHERE partner.company.peppolId = :ownerPeppolId
            AND partner.peppolId = :peppolId ORDER BY partner.name DESC
            """)
    List<Partner> search(String ownerPeppolId, String peppolId);
}

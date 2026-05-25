package org.letspeppol.app.repository;

import org.letspeppol.app.model.SponsorInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SponsorInvoiceRepository extends JpaRepository<SponsorInvoice, Long> {

    Optional<SponsorInvoice> findTopByOrderByIdDesc();

    List<SponsorInvoice> findAllByOrderBySponsoredOnDesc();

    boolean existsByCompanyIdAndSponsoredOnAfter(Long companyId, Instant sponsoredOn);
}

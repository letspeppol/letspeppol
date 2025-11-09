package io.tubs.app.repository;

import io.tubs.app.model.Partner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PartnerRepository extends JpaRepository<Partner, Long> {
    @Query("SELECT partner FROM Partner partner WHERE partner.company.companyNumber = :companyNumber ORDER BY partner.name DESC")
    List<Partner> findByOwningCompany(String companyNumber);
}

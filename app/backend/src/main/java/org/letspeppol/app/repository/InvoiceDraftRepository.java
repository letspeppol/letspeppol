package org.letspeppol.app.repository;

import org.letspeppol.app.model.InvoiceDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InvoiceDraftRepository extends JpaRepository<InvoiceDraft, Long> {

    @Query("SELECT draft FROM InvoiceDraft draft WHERE draft.company.companyNumber = :companyNumber ORDER BY draft.lastUpdatedOn DESC")
    List<InvoiceDraft> findByOwningCompany(String companyNumber);

    @Modifying
    @Query("DELETE FROM InvoiceDraft draft WHERE draft.id = :id AND draft.company.companyNumber = :companyNumber")
    void deleteByIdAndCompanyNumber(String companyNumber, Long id);

}


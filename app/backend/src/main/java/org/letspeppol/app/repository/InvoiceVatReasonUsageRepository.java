package org.letspeppol.app.repository;

import org.letspeppol.app.model.InvoiceVatReasonUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InvoiceVatReasonUsageRepository extends JpaRepository<InvoiceVatReasonUsage, Long> {
    void deleteByDocumentId(UUID documentId);
}

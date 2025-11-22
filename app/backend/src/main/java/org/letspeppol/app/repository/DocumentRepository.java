package org.letspeppol.app.repository;

import org.letspeppol.app.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Collection<Document> findAllByOwnerPeppolId(String peppolId);

    void deleteByIdAndOwnerPeppolId(UUID id, String peppolId);
}

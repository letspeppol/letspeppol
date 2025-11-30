package org.letspeppol.app.repository;

import org.letspeppol.app.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

//    @Query("SELECT document FROM Document document WHERE document.company.peppolId = :peppolId ORDER BY document.createdOn DESC")
    Collection<Document> findAllByOwnerPeppolId(String peppolId);

//    @Modifying
//    @Query("DELETE FROM Document document WHERE document.id = :id AND document.company.peppolId = :peppolId")
    void deleteByIdAndOwnerPeppolId(UUID id, String peppolId);
}

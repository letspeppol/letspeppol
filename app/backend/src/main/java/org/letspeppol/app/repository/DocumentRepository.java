package org.letspeppol.app.repository;

import org.letspeppol.app.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

}

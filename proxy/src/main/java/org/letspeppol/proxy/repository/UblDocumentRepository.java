package org.letspeppol.proxy.repository;

import org.letspeppol.proxy.model.UblDocument;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.letspeppol.proxy.model.DocumentDirection;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UblDocumentRepository extends JpaRepository<UblDocument, UUID> {
    List<UblDocument> findAllByHash(String hash);

    Optional<UblDocument> findByIdAndOwnerPeppolId(UUID id, String ownerPeppolId);

    Slice<UblDocument> findAllByOwnerPeppolIdAndDownloadCountAndDirection(String ownerPeppolId, Integer downloadCount, DocumentDirection direction, Pageable pageable);

    List<UblDocument> findAllByDirectionAndScheduledOnBeforeAndAccessPointIsNull(DocumentDirection direction, Instant before, Pageable pageable);

    Optional<UblDocument> findByAccessPointId(String accessPointId);
}

package org.letspeppol.proxy.service;

import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.dto.DocumentDetailsDto;
import org.letspeppol.proxy.dto.UblDocumentDto;
import org.letspeppol.proxy.exception.ConflictException;
import org.letspeppol.proxy.exception.NotFoundException;
import org.letspeppol.proxy.mapper.UblDocumentMapper;
import org.letspeppol.proxy.model.DocumentDirection;
import org.letspeppol.proxy.model.UblDocument;
import org.letspeppol.proxy.repository.UblDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Transactional
@Service
public class UblDocumentService {

    private final UblDocumentRepository ublDocumentRepository;
    private final AccessPointServiceRegistry accessPointServiceRegistry;

    //TODO : find all archived

    public UblDocumentDto findById(UUID id, String ownerPeppolId) {
        return UblDocumentMapper.toDto(ublDocumentRepository.findByIdAndOwnerPeppolId(id, ownerPeppolId).orElseThrow(() -> new NotFoundException("UblDocument "+id+" does not exist")));
    }

    public List<UblDocumentDto> findByIds(List<UUID> ids, String ownerPeppolId) {
        return ublDocumentRepository.findByIdInAndOwnerPeppolId(ids, ownerPeppolId).stream()
                .map(UblDocumentMapper::toDto)
                .toList();
    }

    public DocumentDetailsDto findDetailsById(UUID id, String ownerPeppolId) {
        UblDocument ublDocument = ublDocumentRepository.findByIdAndOwnerPeppolId(id, ownerPeppolId)
                .orElseThrow(() -> new NotFoundException("UblDocument " + id + " does not exist"));
        if (!DocumentDirection.OUTGOING.equals(ublDocument.getDirection()) || ublDocument.getProcessedOn() == null) {
            throw new ConflictException("Delivery details are only available for processed outgoing documents");
        }
        Map<String, Object> details = getAccessPointDetails(ublDocument);
        return toDetailsDto(ublDocument, details);
    }

    private Map<String, Object> getAccessPointDetails(UblDocument ublDocument) {
        Map<String, Object> details = ublDocument.getAccessPointDetails();
        if (details != null && !details.isEmpty()) {
            return details;
        }
        if (ublDocument.getAccessPoint() == null || ublDocument.getAccessPointId() == null) {
            throw new ConflictException("Delivery details are not available");
        }
        AccessPointServiceInterface service = accessPointServiceRegistry.get(ublDocument.getAccessPoint());
        if (service == null) {
            throw new ConflictException("Delivery details are not available for access point " + ublDocument.getAccessPoint());
        }
        details = service.getDeliveryDetails(ublDocument);
        if (details == null || details.isEmpty()) {
            throw new ConflictException("Delivery details are not available for access point " + ublDocument.getAccessPoint());
        }
        ublDocument.setAccessPointDetails(details);
        return details;
    }

    private DocumentDetailsDto toDetailsDto(UblDocument ublDocument, Map<String, Object> details) {
        return new DocumentDetailsDto(
                ublDocument.getId(),
                ublDocument.getOwnerPeppolId(),
                ublDocument.getPartnerPeppolId(),
                ublDocument.getAccessPoint() == null ? null : ublDocument.getAccessPoint().name(),
                ublDocument.getAccessPointId(),
                ublDocument.getProcessedOn(),
                ublDocument.getProcessedStatus(),
                valueAsString(details, "peppolC3SeatID"),
                valueAsString(details, "peppolC3MessageID"),
                valueAsInstant(details, "peppolC3Timestamp")
        );
    }

    private String valueAsString(Map<String, Object> details, String key) {
        Object value = details.get(key);
        return value == null ? null : value.toString();
    }

    private Instant valueAsInstant(Map<String, Object> details, String key) {
        String value = valueAsString(details, key);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

}

package org.letspeppol.proxy.service;

import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.dto.UblDocumentDto;
import org.letspeppol.proxy.exception.BadRequestException;
import org.letspeppol.proxy.exception.DuplicateRequestException;
import org.letspeppol.proxy.exception.NotFoundException;
import org.letspeppol.proxy.mapper.UblDocumentMapper;
import org.letspeppol.proxy.model.DocumentDirection;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.UblDocument;
import org.letspeppol.proxy.repository.UblDocumentRepository;
import org.letspeppol.proxy.util.HashUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Transactional
@Service
public class UblDocumentService {

    public static final String DEFAULT_CONTENT_FOR_NO_ARCHIVE = "No Archive";

    private final UblDocumentRepository ublDocumentRepository;
    private final RegistryService registryService;
    private final AccessPointServiceRegistry accessPointServiceRegistry;

    private Path backupFilePath(UblDocument ublDocument) {
        return Paths.get(
                //System.getProperty("java.io.tmpdir"),
                "backup",
                ublDocument.getOwnerPeppolId(),
                ublDocument.getDirection().toString(),
                String.valueOf(ublDocument.getCreatedOn().atZone(ZoneId.systemDefault()).getYear()),
                String.valueOf(ublDocument.getCreatedOn().atZone(ZoneId.systemDefault()).getMonth()),
                ublDocument.getId() + ".ubl"
        );
    }

    private void backupFile(UblDocument ublDocument) {
        Path filePath = backupFilePath(ublDocument);
        Path parent = filePath.getParent();
        try {
            if (parent != null) {
                System.out.println("Writing backup folder to: " + parent);
                Files.createDirectories(parent);
            }
            System.out.println("Writing file as backup to: " + filePath);
            Files.writeString(filePath, ublDocument.getUbl(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void clearBackupFile(UblDocument ublDocument) {
        Path filePath = backupFilePath(ublDocument);
        try {
            Files.writeString(filePath, DEFAULT_CONTENT_FOR_NO_ARCHIVE, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Instant calculateSchedule(UblDocumentDto ublDocumentDto) {
        //TODO : find number that are scheduled on that moment
        return ublDocumentDto.scheduledOn();
    }

    public void sendDueOutgoing() {
        List<UblDocument> ublDocuments = ublDocumentRepository.findAllByDirectionAndScheduledOnBeforeAndAccessPointIsNull(
                DocumentDirection.OUTGOING,
                Instant.now(),
                PageRequest.of(
                        0,
                        1,
                        Sort.by("scheduledOn").ascending().and(
                                Sort.by("createdOn").ascending()
                        )
                )
        );
        for (UblDocument ublDocument : ublDocuments) {
            sendToAccessPoint(ublDocument);
            System.out.print("!"); //TODO : monitoring ?
        }
        System.out.print("."); //TODO : monitoring ?
    }

    private void sendToAccessPoint(UblDocument ublDocument) {
        AccessPoint accessPoint = registryService.getAccessPoint(ublDocument.getOwnerPeppolId());
        if (accessPoint == AccessPoint.NONE) {
            pickedUp(ublDocument, accessPoint, null);
            delivered(ublDocument, "PeppolId is not registered to send");
            return;
        }
        AccessPointServiceInterface service = accessPointServiceRegistry.get(accessPoint);
        if (service == null) {
            pickedUp(ublDocument, accessPoint, null);
            delivered(ublDocument, "Peppol Access Point not active");
            return;
        }
        String accessPointId = service.sendDocument(ublDocument);
        if (accessPointId == null) {
            ublDocument.setScheduledOn(ublDocument.getScheduledOn().plus(1, ChronoUnit.HOURS)); //Postpone 1 hour to try again
            return;
        }
        pickedUp(ublDocument, accessPoint, accessPointId);
    }

    public List<UblDocumentDto> findAllNew(String ownerPeppolId, int limit) {
        var pageable = PageRequest.of(0, limit, Sort.by("createdOn").descending());
        return ublDocumentRepository.findAllByOwnerPeppolIdAndDownloadCountAndDirection(ownerPeppolId, 0, DocumentDirection.INCOMING, pageable).stream()
                .map(UblDocumentMapper::toDto)
                .toList();
    }

    //TODO : find all archived

    public UblDocumentDto findById(UUID id, String ownerPeppolId) {
        return UblDocumentMapper.toDto(ublDocumentRepository.findByIdAndOwnerPeppolId(id, ownerPeppolId).orElseThrow(() -> new NotFoundException("UblDocument "+id+" does not exist")));
    }

    public UblDocumentDto createToSend(UblDocumentDto ublDocumentDto, boolean noArchive) {
        String hash = HashUtil.sha256(ublDocumentDto.ubl());
        if (ublDocumentRepository.findById(ublDocumentDto.id()).isPresent() || !ublDocumentRepository.findAllByHash(hash).isEmpty()) //TODO : should we add timeframe based on ublDocumentDto.createdOn() ?
            throw new DuplicateRequestException("UblDocument "+ublDocumentDto.id()+" is already send");

        UblDocument ublDocument = new UblDocument( //TODO : do we set default values here ?
                ublDocumentDto.id(),
                DocumentDirection.OUTGOING, //user can not overwrite this value : ublDocumentDto.direction(),
                ublDocumentDto.ownerPeppolId(),
                ublDocumentDto.partnerPeppolId(),
                null, //TODO : would we want to listen to external createdOn : ublDocumentDto.createdOn(),
                calculateSchedule(ublDocumentDto),
                null,
                null,
                ublDocumentDto.ubl(),
                hash,
                noArchive?-1:0,
                null,
                null,
                null
        );
        ublDocumentRepository.save(ublDocument); //This is needed as it is a new
        backupFile(ublDocument);
        return UblDocumentMapper.toDto(ublDocument);
    }

    public void pickedUp(UUID id, AccessPoint accessPoint, String accessPointId) {
        UblDocument ublDocument = ublDocumentRepository.findById(id).orElseThrow(() -> new NotFoundException("UblDocument " + id + " does not exist"));
        pickedUp(ublDocument, accessPoint, accessPointId);
    }

    public void pickedUp(UblDocument ublDocument, AccessPoint accessPoint, String accessPointId) {
        ublDocument.setAccessPoint(accessPoint);
        ublDocument.setAccessPointId(accessPointId);
        if (ublDocument.getDownloadCount() < 0) { //Set to No-Archive, removed once the Peppol AP received it, if it fails the End-User can send it again as owner of the data
            ublDocument.setUbl(null);
            ublDocument.setDownloadCount(0);
            clearBackupFile(ublDocument);
        }
        // ublDocumentRepository.save(ublDocument); //This can be remove due to @Transactional
    }

    public void delivered(UUID id, String status) {
        UblDocument ublDocument = ublDocumentRepository.findById(id).orElseThrow(() -> new NotFoundException("UblDocument " + id + " does not exist"));
        delivered(ublDocument, status);
    }

    public void delivered(UblDocument ublDocument, String status) {
        ublDocument.setProcessedOn(Instant.now());
        ublDocument.setProcessedStatus(status);
        if (ublDocument.getDownloadCount() < 0) { //Set to No-Archive
            ublDocument.setUbl(null);
            ublDocument.setDownloadCount(0);
            clearBackupFile(ublDocument);
        }
        // ublDocumentRepository.save(ublDocument); //This can be remove due to @Transactional
    }

    public void reschedule(UUID id, String ownerPeppolId, UblDocumentDto ublDocumentDto) {
        UblDocument ublDocument = ublDocumentRepository.findByIdAndOwnerPeppolId(id, ownerPeppolId).orElseThrow(() -> new NotFoundException("UblDocument "+id+" does not exist"));
        if (ublDocument.getAccessPoint() != null) {
            throw new BadRequestException("UblDocument "+id+" is already picked up by AP");
        }
        Instant calculatedSchedule = calculateSchedule(ublDocumentDto);
        if (!calculatedSchedule.equals(ublDocument.getScheduledOn())) {
            ublDocument.setScheduledOn(calculatedSchedule);
        }
        // ublDocumentRepository.save(ublDocument); //This can be remove due to @Transactional
    }

    public void cancel(UUID id, String ownerPeppolId, boolean noArchive) {
        UblDocument ublDocument = ublDocumentRepository.findByIdAndOwnerPeppolId(id, ownerPeppolId).orElseThrow(() -> new NotFoundException("UblDocument "+id+" does not exist"));
        if (ublDocument.getAccessPoint() != null) {
            throw new BadRequestException("UblDocument "+id+" is already picked up by AP");
        }
        ublDocument.setAccessPoint(AccessPoint.NONE);
        if (noArchive || ublDocument.getDownloadCount() < 0) { //Set to No-Archive
            ublDocument.setUbl(null);
            ublDocument.setDownloadCount(0);
            clearBackupFile(ublDocument);
        }
        // ublDocumentRepository.save(ublDocument); //This can be remove due to @Transactional
    }

    public void createAsReceived(String senderPeppolId, String receiverPeppolId, String ubl, AccessPoint accessPoint, String accessPointId, Runnable afterCommit) {
        String hash = HashUtil.sha256(ubl);
        if (ublDocumentRepository.findByAccessPointId(accessPointId).isPresent() || !ublDocumentRepository.findAllByHash(hash).isEmpty()) //TODO : should we add timeframe based on ublDocumentDto.createdOn() ?
            throw new DuplicateRequestException("UblDocument "+accessPointId+" is already received"); //TODO : does this make sense as AP needs to be informed we have successfully received the document ?

        UblDocument ublDocument = new UblDocument( //TODO : do we set default values here ?
                UUID.randomUUID(), //No autogeneration used
                DocumentDirection.INCOMING, //AP can not overwrite this value : ublDocumentDto.direction(),
                receiverPeppolId,
                senderPeppolId,
                null, //TODO : would we want to listen to external createdOn : ublDocumentDto.createdOn(),
                null,
                Instant.now(),
                null,
                ubl,
                hash,
                0,
                null,
                accessPoint,
                accessPointId
        );
        ublDocument = ublDocumentRepository.save(ublDocument); //This is needed as it is a new
        try {
            backupFile(ublDocument);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (afterCommit != null && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    afterCommit.run();
                }
            });
        } else if (afterCommit != null) {
            // Fallback if someone calls this without a TX (shouldn’t happen here)
            afterCommit.run();
        }
    }

    public void downloaded(List<UUID> ids, String ownerPeppolId, boolean noArchive) {
        for (UUID id : ids) {
            UblDocument ublDocument = ublDocumentRepository.findByIdAndOwnerPeppolId(id, ownerPeppolId).orElseThrow(() -> new NotFoundException("UblDocument "+id+" does not exist"));
            if (ublDocument.getAccessPoint() == null) {
                continue; //Ignoring unprocessed ubl documents
            }
            ublDocument.setDownloadCount(ublDocument.getDownloadCount() + 1);
            if (noArchive) { //Set to No-Archive
                ublDocument.setUbl(null);
                clearBackupFile(ublDocument);
            }
            // ublDocumentRepository.save(ublDocument); //This can be remove due to @Transactional
        }
    }

}

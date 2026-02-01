package org.letspeppol.proxy.service;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.UblDocumentDto;
import org.letspeppol.proxy.exception.DuplicateRequestException;
import org.letspeppol.proxy.exception.NotFoundException;
import org.letspeppol.proxy.mapper.UblDocumentMapper;
import org.letspeppol.proxy.model.*;
import org.letspeppol.proxy.repository.AppLinkRepository;
import org.letspeppol.proxy.repository.UblDocumentRepository;
import org.letspeppol.proxy.util.HashUtil;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class UblDocumentReceiverService {

    private final UblDocumentRepository ublDocumentRepository;
    private final AppLinkRepository appLinkRepository;
    private final BackupService backupService;
    private final BalanceService balanceService;
    private final Counter documentReceivedCounter;

    public List<UblDocumentDto> findAllNew(String ownerPeppolId, int limit) {
        var pageable = PageRequest.of(0, limit, Sort.by("createdOn").ascending());
        return ublDocumentRepository.findAllByOwnerPeppolIdAndDownloadCountAndDirection(ownerPeppolId, 0, DocumentDirection.INCOMING, pageable)
                .stream()
                .map(UblDocumentMapper::toDto)
                .toList();
    }

    public List<UblDocumentDto> findAllNewByAppLink(UUID uid, int limit) {
        var pageable = PageRequest.of(0, limit, Sort.by("createdOn").descending());
        return ublDocumentRepository.findAllNewByLinkedUid(uid, 0, DocumentDirection.INCOMING, pageable)
                .stream()
                .map(UblDocumentMapper::toDto)
                .toList();
    }

    public void createAsReceived(DocumentType documentType, String senderPeppolId, String receiverPeppolId, String ubl, AccessPoint accessPoint, String accessPointId, Runnable afterCommit) {
        String hash = HashUtil.sha256(ubl);
        if (ublDocumentRepository.findByAccessPointId(accessPointId).isPresent() || !ublDocumentRepository.findAllByHash(hash).isEmpty()) {
            log.error("Receiving duplicate document {} from Access Point {}", accessPointId, accessPoint);
            afterCommit.run(); //TODO : does this make sense as AP needs to be informed we have successfully received the document ?
            throw new DuplicateRequestException("UblDocument " + accessPointId + " is already received");
        }

        UblDocument ublDocument = new UblDocument(
                UUID.randomUUID(), //No autogeneration used
                DocumentDirection.INCOMING, //AP can not overwrite this value : ublDocumentDto.direction(),
                documentType,
                receiverPeppolId,
                senderPeppolId,
                Instant.now(), //setting manual because we need this value to return and @Transactional will postpone it and the return dto has null
                null,
                Instant.now(),
                null,
                ubl,
                hash,
                0,
                null,
                accessPoint,
                accessPointId //Unique
        );
        ublDocument = ublDocumentRepository.save(ublDocument); //This is needed as it is a new
        try {
            backupService.backupFile(ublDocument);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        log.info("Successful received document {} from Peppol Access Point {} | balance = {} ", ublDocument.getId(), accessPoint, balanceService.decrement());
        if (afterCommit != null && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                public void afterCommit() {
                    afterCommit.run();
                    documentReceivedCounter.increment();
                }
            });
        } else if (afterCommit != null) {
            // Fallback if someone calls this without a TX (shouldn’t happen here)
            afterCommit.run();
        }
    }

    public void downloaded(List<UUID> ids, String ownerPeppolId, boolean noArchive) {
        downloaded(ids, ownerPeppolId, null, noArchive);
    }

    public void downloaded(List<UUID> ids, UUID appUid, boolean noArchive) {
        downloaded(ids, null, appUid, noArchive);
    }

    public void downloaded(List<UUID> ids, String ownerPeppolId, UUID appUid, boolean noArchive) {
        for (UUID id : ids) {
            Optional<UblDocument> optionalUblDocument = ublDocumentRepository.findById(id);
            if (optionalUblDocument.isEmpty()) {
                log.error("Document {} not found for {} when trying to flag as downloaded", id, ownerPeppolId);
                continue;
            }
            UblDocument ublDocument = optionalUblDocument.get();
            if (ownerPeppolId != null && !ublDocument.getOwnerPeppolId().equals(ownerPeppolId)) {
                log.error("Document {} is owned by {} and not {} when trying to flag as downloaded", id, ublDocument.getOwnerPeppolId(), ownerPeppolId);
                continue;
            }
            if (ownerPeppolId == null && appLinkRepository.existsById(new AppLink.AppLinkId(ublDocument.getOwnerPeppolId(), appUid))) {
                log.error("Document {} is owned by {} and App {} has no access when trying to flag as downloaded", id, ublDocument.getOwnerPeppolId(), appUid);
                continue;
            }
            if (ublDocument.getAccessPoint() == null) {
                log.error("Document {} is not yet processed for {} when trying to flag as downloaded", id, ownerPeppolId);
                continue; //Ignoring unprocessed ubl documents
            }
            ublDocument.setDownloadCount(ublDocument.getDownloadCount() + 1);
            if (noArchive) { //Set to No-Archive
                ublDocument.setUbl(null);
                backupService.clearBackupFile(ublDocument);
            }
            // ublDocument = ublDocumentRepository.save(ublDocument); //This can be remove due to @Transactional
        }
    }

}

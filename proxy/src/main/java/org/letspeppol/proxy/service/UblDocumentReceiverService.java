package org.letspeppol.proxy.service;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.UblDocumentDto;
import org.letspeppol.proxy.exception.DuplicateRequestException;
import org.letspeppol.proxy.exception.NotFoundException;
import org.letspeppol.proxy.mapper.UblDocumentMapper;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.DocumentDirection;
import org.letspeppol.proxy.model.DocumentType;
import org.letspeppol.proxy.model.UblDocument;
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
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class UblDocumentReceiverService {

    private final UblDocumentRepository ublDocumentRepository;
    private final BackupService backupService;
    private final BalanceService balanceService;
    private final Counter documentReceivedCounter;

    public List<UblDocumentDto> findAllNew(String ownerPeppolId, int limit) {
        var pageable = PageRequest.of(0, limit, Sort.by("createdOn").descending());
        return ublDocumentRepository.findAllByOwnerPeppolIdAndDownloadCountAndDirection(ownerPeppolId, 0, DocumentDirection.INCOMING, pageable).stream()
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
        for (UUID id : ids) {
            UblDocument ublDocument = ublDocumentRepository.findByIdAndOwnerPeppolId(id, ownerPeppolId).orElseThrow(() -> new NotFoundException("UblDocument "+id+" does not exist"));
            if (ublDocument.getAccessPoint() == null) {
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

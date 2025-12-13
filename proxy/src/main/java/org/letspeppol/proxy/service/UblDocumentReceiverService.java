package org.letspeppol.proxy.service;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.exception.DuplicateRequestException;
import org.letspeppol.proxy.exception.NotFoundException;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.DocumentDirection;
import org.letspeppol.proxy.model.DocumentType;
import org.letspeppol.proxy.model.UblDocument;
import org.letspeppol.proxy.repository.UblDocumentRepository;
import org.letspeppol.proxy.util.HashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Transactional
@Service
public class UblDocumentReceiverService {

    private final UblDocumentRepository ublDocumentRepository;
    private final BackupService backupService;
    private final Counter documentReceivedCounter;

    public void createAsReceived(DocumentType documentType, String senderPeppolId, String receiverPeppolId, String ubl, AccessPoint accessPoint, String accessPointId, Runnable afterCommit) {
        String hash = HashUtil.sha256(ubl);
        if (ublDocumentRepository.findByAccessPointId(accessPointId).isPresent() || !ublDocumentRepository.findAllByHash(hash).isEmpty()) //TODO : should we add timeframe based on ublDocumentDto.createdOn() ?
            throw new DuplicateRequestException("UblDocument "+accessPointId+" is already received"); //TODO : does this make sense as AP needs to be informed we have successfully received the document ?

        UblDocument ublDocument = new UblDocument( //TODO : do we set default values here ?
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

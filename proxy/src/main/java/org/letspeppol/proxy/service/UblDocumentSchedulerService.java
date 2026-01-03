package org.letspeppol.proxy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.StatusReport;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.DocumentDirection;
import org.letspeppol.proxy.model.UblDocument;
import org.letspeppol.proxy.repository.UblDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import static java.lang.Math.max;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class UblDocumentSchedulerService {

    private final UblDocumentRepository ublDocumentRepository;
    private final RegistryService registryService;
    private final AccessPointServiceRegistry accessPointServiceRegistry;
    private final BackupService backupService;
    private final BalanceService balanceService;

    @Value("${scheduler.send.slow-down-factor:2}")
    private int sendSlowDownFactor;
    @Value("${scheduler.synchronize.limit-per-interval:60}")
    private int synchronizeLimitPerInterval;

    public void sendDueOutgoing() {
        List<UblDocument> ublDocuments = ublDocumentRepository.findAllByDirectionAndScheduledOnBeforeAndAccessPointIsNull(
                DocumentDirection.OUTGOING,
                Instant.now(),
                PageRequest.of(
                        0,
                        max((int) ( balanceService.get() / sendSlowDownFactor ), 1), //Will process more as long as the balance is positive, slowing down by halving per second
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
            delivered(ublDocument, new StatusReport(false, "Proxy error : PeppolId is not registered to send"));
            return;
        }
        AccessPointServiceInterface service = accessPointServiceRegistry.get(accessPoint);
        if (service == null) {
            pickedUp(ublDocument, accessPoint, null);
            delivered(ublDocument, new StatusReport(false, "Proxy error : Peppol Access Point not active"));
            return;
        }
        if (ublDocument.getPartnerPeppolId().equals(ublDocument.getOwnerPeppolId())) {
            String loopbackId = loopbackDocument(ublDocument);
            log.info("Successful processed loopback document {} bypassed Peppol Access Point {} | balance = {} ", ublDocument.getId(), accessPoint, balanceService.decrement());
            pickedUp(ublDocument, AccessPoint.LOOPBACK, loopbackId.toString());
            return;
        }
        String accessPointId = service.sendDocument(ublDocument);
        if (accessPointId == null) {
            ublDocument.setScheduledOn(ublDocument.getScheduledOn().plus(1, ChronoUnit.HOURS)); //Postpone 1 hour to try again
            return;
        }
        log.info("Successful send document {} to Peppol Access Point {} | balance = {} ", ublDocument.getId(), accessPoint, balanceService.decrement());
        pickedUp(ublDocument, accessPoint, accessPointId);
    }

    public void synchronizeOutgoingDocuments() {
        List<UblDocument> ublDocuments = ublDocumentRepository.findAllByDirectionAndProcessedOnIsNullAndAccessPointIsNotNull(
                DocumentDirection.OUTGOING,
                PageRequest.of(
                        0,
                        synchronizeLimitPerInterval,
                        Sort.by("updatedOn").ascending()
                )
        );
        for (UblDocument ublDocument : ublDocuments) {
            if (ublDocument.getUpdatedOn().isAfter(Instant.now().plus(1, ChronoUnit.DAYS))) {
                log.warn("Document {} send to Peppol Access Point {} is still not processed since {}", ublDocument.getId(), ublDocument.getAccessPoint(), ublDocument.getUpdatedOn());
            }
            synchronizeWithAccessPoint(ublDocument);
            System.out.print("-"); //TODO : monitoring ?
        }
        System.out.print(","); //TODO : monitoring ?
    }

    private void synchronizeWithAccessPoint(UblDocument ublDocument) {
//        AccessPoint accessPoint = registryService.getAccessPoint(ublDocument.getOwnerPeppolId());
//        if (accessPoint == AccessPoint.NONE) {
//            delivered(ublDocument, new StatusReport(false, "Proxy error : PeppolId is no longer registered to synchronize"));
//            return;
//        }
        AccessPoint accessPoint = ublDocument.getAccessPoint(); //Take the Access Point of the document
        if (accessPoint == AccessPoint.LOOPBACK) {
            log.warn("Loopback document {} was not delivered since {}", ublDocument.getId(), ublDocument.getUpdatedOn());
            delivered(ublDocument, new StatusReport(true, null));
            return;
        }
        AccessPointServiceInterface service = accessPointServiceRegistry.get(accessPoint);
        if (service == null) {
            delivered(ublDocument, new StatusReport(false, "Proxy error : Peppol Access Point no longer active"));
            return;
        }
        StatusReport statusReport = service.getStatus(ublDocument);
        if (statusReport == null) {
            return;
        }
        delivered(ublDocument, statusReport);
    }

    private void pickedUp(UblDocument ublDocument, AccessPoint accessPoint, String accessPointId) {
        ublDocument.setAccessPoint(accessPoint);
        ublDocument.setAccessPointId(accessPointId);
        if (ublDocument.getDownloadCount() < 0) { //Set to No-Archive, removed once the Peppol AP received it, if it fails the End-User can send it again as owner of the data
            ublDocument.setUbl(null);
            ublDocument.setDownloadCount(0);
            backupService.clearBackupFile(ublDocument);
        }
        // ublDocument = ublDocumentRepository.save(ublDocument); //This can be remove due to @Transactional
    }

    private void delivered(UblDocument ublDocument, StatusReport statusReport) {
        ublDocument.setProcessedOn(Instant.now());
        ublDocument.setProcessedStatus( statusReport.success() ? null : statusReport.statusMessage() );
        if (ublDocument.getDownloadCount() < 0) { //Set to No-Archive
            ublDocument.setUbl(null);
            ublDocument.setDownloadCount(0);
            backupService.clearBackupFile(ublDocument);
        }
        // ublDocument = ublDocumentRepository.save(ublDocument); //This can be remove due to @Transactional
    }

    private String loopbackDocument(UblDocument ublDocument) {
        UblDocument loopbackUblDocument = new UblDocument(
                UUID.randomUUID(), //No autogeneration used
                DocumentDirection.INCOMING,
                ublDocument.getType(),
                ublDocument.getPartnerPeppolId(),
                ublDocument.getOwnerPeppolId(),
                Instant.now(),
                null,
                Instant.now(),
                null,
                ublDocument.getUbl(),
                ublDocument.getHash(),
                0,
                null,
                AccessPoint.LOOPBACK,
                ublDocument.getId().toString()
        );
        loopbackUblDocument = ublDocumentRepository.save(loopbackUblDocument);
        try {
            backupService.backupFile(loopbackUblDocument);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return loopbackUblDocument.getId().toString();
    }
}

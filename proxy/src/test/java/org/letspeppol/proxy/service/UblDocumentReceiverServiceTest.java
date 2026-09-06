package org.letspeppol.proxy.service;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.DocumentDirection;
import org.letspeppol.proxy.model.DocumentType;
import org.letspeppol.proxy.model.UblDocument;
import org.letspeppol.proxy.repository.AppLinkRepository;
import org.letspeppol.proxy.repository.UblDocumentRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UblDocumentReceiverServiceTest {

    @Mock
    private UblDocumentRepository ublDocumentRepository;
    @Mock
    private AppLinkRepository appLinkRepository;
    @Mock
    private BackupService backupService;
    @Mock
    private BalanceService balanceService;
    @Mock
    private Counter documentReceivedCounter;

    @InjectMocks
    private UblDocumentReceiverService service;

    @Test
    void findAllNewDoesNotExposeOutgoingDocumentBeforeAccessPointPickup() {
        String ownerPeppolId = "0208:0685912734";
        UblDocument waitingForPickup = outgoingDocument(ownerPeppolId, null);
        UblDocument pickedUp = outgoingDocument(ownerPeppolId, AccessPoint.SCRADA);
        when(ublDocumentRepository.findAllNewByOwnerPeppolId(
                eq(ownerPeppolId),
                eq(0),
                eq(List.of(DocumentDirection.INCOMING, DocumentDirection.OUTGOING)),
                any(Pageable.class)
        )).thenReturn(List.of(waitingForPickup, pickedUp));

        var result = service.findAllNew(ownerPeppolId, 100);

        assertThat(result)
                .extracting(document -> document.id())
                .containsExactly(pickedUp.getId());
    }

    @Test
    void findAllNewByAppLinkDoesNotExposeOutgoingDocumentBeforeAccessPointPickup() {
        UUID appUid = UUID.randomUUID();
        UblDocument waitingForPickup = outgoingDocument("0208:0685912734", null);
        UblDocument pickedUp = outgoingDocument("0208:0685912734", AccessPoint.SCRADA);
        when(ublDocumentRepository.findAllNewByLinkedUid(
                eq(appUid),
                eq(0),
                eq(List.of(DocumentDirection.INCOMING, DocumentDirection.OUTGOING)),
                any(Pageable.class)
        )).thenReturn(List.of(waitingForPickup, pickedUp));

        var result = service.findAllNewByAppLink(appUid, 100);

        assertThat(result)
                .extracting(document -> document.id())
                .containsExactly(pickedUp.getId());
    }

    private UblDocument outgoingDocument(String ownerPeppolId, AccessPoint accessPoint) {
        UblDocument document = new UblDocument();
        document.setId(UUID.randomUUID());
        document.setDirection(DocumentDirection.OUTGOING);
        document.setType(DocumentType.INVOICE);
        document.setOwnerPeppolId(ownerPeppolId);
        document.setPartnerPeppolId("0208:0123456789");
        document.setCreatedOn(Instant.now());
        document.setScheduledOn(Instant.now());
        document.setUbl("<Invoice/>");
        document.setHash(UUID.randomUUID().toString());
        document.setDownloadCount(0);
        document.setAccessPoint(accessPoint);
        return document;
    }
}

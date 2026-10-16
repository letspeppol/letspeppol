package org.letspeppol.proxy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.DocumentDirection;
import org.letspeppol.proxy.model.UblDocument;
import org.letspeppol.proxy.repository.UblDocumentRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UblDocumentServiceTest {

    private static final Instant RECEIVED_ON = Instant.parse("2026-10-16T10:00:01Z");
    private static final String RECEIPT = """
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
                           xmlns:eb="http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/">
              <soap:Header>
                <eb:Messaging>
                  <eb:UserMessage><eb:MessageInfo><eb:MessageId>unrelated-id</eb:MessageId></eb:MessageInfo></eb:UserMessage>
                  <eb:SignalMessage>
                    <eb:MessageInfo>
                      <eb:Timestamp>2026-10-16T10:00:01Z</eb:Timestamp>
                      <eb:MessageId>receipt-1@receiver</eb:MessageId>
                      <eb:RefToMessageId>message-1@recommand</eb:RefToMessageId>
                    </eb:MessageInfo>
                    <eb:Receipt/>
                  </eb:SignalMessage>
                </eb:Messaging>
              </soap:Header>
              <soap:Body/>
            </soap:Envelope>
            """;

    @Mock
    private UblDocumentRepository repository;
    @Mock
    private AccessPointServiceRegistry accessPoints;
    @InjectMocks
    private UblDocumentService service;

    private UblDocument document;

    @BeforeEach
    void setUp() {
        document = new UblDocument();
        document.setId(UUID.randomUUID());
        document.setOwnerPeppolId("0208:0685912734");
        document.setPartnerPeppolId("0208:0123456789");
        document.setDirection(DocumentDirection.OUTGOING);
        document.setAccessPoint(AccessPoint.RECOMMAND);
        document.setAccessPointId("document-1");
        document.setProcessedOn(RECEIVED_ON);
        when(repository.findByIdAndOwnerPeppolId(document.getId(), document.getOwnerPeppolId()))
                .thenReturn(Optional.of(document));
    }

    @Test
    void mapsTheReceiverReceiptFromPreviouslyCachedRecommandDetails() {
        document.setAccessPointDetails(recommandDetails(RECEIPT));

        var result = service.findDetailsById(document.getId(), document.getOwnerPeppolId());

        assertThat(result.partnerPeppolMessageId()).isEqualTo("receipt-1@receiver");
        assertThat(result.partnerPeppolMessageOn()).isEqualTo(RECEIVED_ON);
        assertThat(result.partnerPeppolAccessPoint()).isNull();
        assertThat(result.accessPointId()).isEqualTo("document-1");
        assertThat(result.processedStatus()).isNull();
        verifyNoInteractions(accessPoints);
    }

    @Test
    void mapsAndCachesFreshRecommandDetails() {
        var accessPoint = mock(AccessPointServiceInterface.class);
        when(accessPoints.get(AccessPoint.RECOMMAND)).thenReturn(accessPoint);
        when(accessPoint.getDeliveryDetails(document)).thenReturn(recommandDetails(RECEIPT));

        var result = service.findDetailsById(document.getId(), document.getOwnerPeppolId());

        assertThat(result.partnerPeppolMessageId()).isEqualTo("receipt-1@receiver");
        assertThat(result.partnerPeppolMessageOn()).isEqualTo(RECEIVED_ON);
        assertThat(document.getAccessPointDetails()).isEqualTo(recommandDetails(RECEIPT));
    }

    @Test
    void acceptsDefaultNamespacesAndOffsetTimestamps() {
        String receipt = RECEIPT.replace("eb:", "").replace("xmlns:eb=", "xmlns=")
                .replace("2026-10-16T10:00:01Z", "2026-10-16T12:00:01+02:00");
        document.setAccessPointDetails(recommandDetails(receipt));

        var result = service.findDetailsById(document.getId(), document.getOwnerPeppolId());

        assertThat(result.partnerPeppolMessageId()).isEqualTo("receipt-1@receiver");
        assertThat(result.partnerPeppolMessageOn()).isEqualTo(RECEIVED_ON);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not xml", "<broken>", "<!DOCTYPE x [<!ENTITY id SYSTEM 'file:///unavailable'>]><x>&id;</x>"})
    void unavailableOrInvalidReceiptDoesNotTurnASuccessfulDeliveryIntoARejection(String receipt) {
        document.setAccessPointDetails(recommandDetails(receipt));

        var result = service.findDetailsById(document.getId(), document.getOwnerPeppolId());

        assertThat(result.partnerPeppolMessageId()).isNull();
        assertThat(result.partnerPeppolMessageOn()).isNull();
        assertThat(result.processedStatus()).isNull();
    }

    @Test
    void doesNotUseTheOutgoingMessageIdWhenNoReceiptIsAvailable() {
        document.setAccessPointDetails(Map.of("sentOverPeppol", true, "peppolMessageId", "message-1@recommand"));

        var result = service.findDetailsById(document.getId(), document.getOwnerPeppolId());

        assertThat(result.partnerPeppolMessageId()).isNull();
        assertThat(result.processedStatus()).isNull();
    }

    @Test
    void doesNotTreatAnErrorSignalAsADeliveryReceipt() {
        document.setAccessPointDetails(recommandDetails(RECEIPT.replace("<eb:Receipt/>", "<eb:Error errorCode='EBMS:0004'/>")));

        var result = service.findDetailsById(document.getId(), document.getOwnerPeppolId());

        assertThat(result.partnerPeppolMessageId()).isNull();
        assertThat(result.partnerPeppolMessageOn()).isNull();
    }

    @Test
    void doesNotUseAReceiptForAnotherTransmission() {
        document.setAccessPointDetails(recommandDetails(RECEIPT.replace("message-1@recommand", "other-message")));

        var result = service.findDetailsById(document.getId(), document.getOwnerPeppolId());

        assertThat(result.partnerPeppolMessageId()).isNull();
    }

    @Test
    void keepsTheReceiptIdWhenItsTimestampIsUnreadable() {
        document.setAccessPointDetails(recommandDetails(RECEIPT.replace("2026-10-16T10:00:01Z", "invalid")));

        var result = service.findDetailsById(document.getId(), document.getOwnerPeppolId());

        assertThat(result.partnerPeppolMessageId()).isEqualTo("receipt-1@receiver");
        assertThat(result.partnerPeppolMessageOn()).isNull();
    }

    @Test
    void mapsScradaDeliveryDetails() {
        document.setAccessPoint(AccessPoint.SCRADA);
        document.setAccessPointDetails(Map.of(
                "peppolC3SeatID", "PBE000001",
                "peppolC3MessageID", "scrada-receipt",
                "peppolC3Timestamp", RECEIVED_ON.toString()
        ));

        var result = service.findDetailsById(document.getId(), document.getOwnerPeppolId());

        assertThat(result.partnerPeppolAccessPoint()).isEqualTo("PBE000001");
        assertThat(result.partnerPeppolMessageId()).isEqualTo("scrada-receipt");
        assertThat(result.partnerPeppolMessageOn()).isEqualTo(RECEIVED_ON);
    }

    private Map<String, Object> recommandDetails(String receipt) {
        return Map.of("id", "document-1", "sentOverPeppol", true,
                "peppolMessageId", "message-1@recommand", "receivedPeppolSignalMessage", receipt);
    }
}

package org.letspeppol.proxy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.proxy.exception.SendAuthorizationException;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.DocumentDirection;
import org.letspeppol.proxy.model.UblDocument;
import org.letspeppol.proxy.repository.UblDocumentRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UblDocumentSchedulerServiceTest {
    private final UblDocumentRepository documents = mock(UblDocumentRepository.class);
    private final RegistryService registry = mock(RegistryService.class);
    private final AccessPointServiceRegistry providers = mock(AccessPointServiceRegistry.class);
    private final AccessPointServiceInterface provider = mock(AccessPointServiceInterface.class);
    private final BackupService backups = mock(BackupService.class);
    private final BalanceService balance = new BalanceService();
    private UblDocumentSchedulerService scheduler;
    private UblDocument document;

    @BeforeEach
    void setUp() {
        scheduler = newScheduler();
        document = outgoing("0208:0685912734");
        when(registry.getAccessPoint(any())).thenReturn(AccessPoint.RECOMMAND);
        when(providers.get(AccessPoint.RECOMMAND)).thenReturn(provider);
        when(documents.findAllByDirectionAndScheduledOnBeforeAndAccessPointIsNull(
                eq(DocumentDirection.OUTGOING), any(), any())).thenReturn(List.of(document));
    }

    @Test
    void confirmedMissingCompanyIsMarkedUnregisteredAndDocumentIsImmediatelyFailed() {
        when(provider.sendDocument(document)).thenThrow(new SendAuthorizationException(true, null));

        scheduler.sendDueOutgoing();

        verify(registry).markNotRegistered(document.getOwnerPeppolId(), AccessPoint.RECOMMAND);
        assertThat(document.getProcessedOn()).isNotNull();
        assertThat(document.getProcessedStatus()).contains("not registered", "RECOMMAND");
        assertThat(document.getAccessPoint()).isEqualTo(AccessPoint.RECOMMAND);
        assertThat(document.getAccessPointId()).isNull();
        assertThat(balance.get()).isZero();
    }

    @Test
    void retriesHourlyAndStopsAfterThreeAttemptsEvenWhenSchedulerIsRecreated() {
        when(provider.sendDocument(document)).thenThrow(new SendAuthorizationException(false, null));

        Instant before = Instant.now();
        scheduler.sendDueOutgoing();
        assertThat(document.getScheduledOn()).isBetween(before.plus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(1)));
        assertThat(document.getProcessedOn()).isNull();
        assertThat(document.getAccessPoint()).isNull();
        // A fresh scheduler reads the retry count from the persisted document, not memory.
        newScheduler().sendDueOutgoing();
        newScheduler().sendDueOutgoing();

        verify(provider, times(3)).sendDocument(document);
        verify(registry, never()).markNotRegistered(any(), any());
        assertThat(document.getProcessedOn()).isNotNull();
        assertThat(document.getProcessedStatus()).contains("could not be authorized", "retry limit reached");
        assertThat(document.getAccessPoint()).isEqualTo(AccessPoint.RECOMMAND);
    }

    @Test
    void expiresAfterOneDayWithoutAnotherSend() {
        document.setAccessPointDetails(retryDetails(Instant.now().minus(Duration.ofDays(1)), 1));

        scheduler.sendDueOutgoing();

        verifyNoInteractions(provider);
        verify(registry, never()).markNotRegistered(any(), any());
        assertThat(document.getProcessedStatus()).contains("retry limit reached");
        assertThat(document.getProcessedOn()).isNotNull();
    }

    @Test
    void retryCannotBeScheduledBeyondTheDeadline() {
        Instant firstFailure = Instant.now().minus(Duration.ofHours(23)).minus(Duration.ofMinutes(30));
        document.setAccessPointDetails(retryDetails(firstFailure, 1));
        when(provider.sendDocument(document)).thenThrow(new SendAuthorizationException(false, null));

        scheduler.sendDueOutgoing();

        assertThat(document.getScheduledOn()).isEqualTo(firstFailure.plus(Duration.ofDays(1)));
        assertThat(document.getProcessedOn()).isNull();
    }

    @Test
    void successfulRetryClearsFailureMetadataAndContinuesNormalDelivery() {
        document.setAccessPointDetails(retryDetails(Instant.now().minus(Duration.ofHours(1)), 1));
        when(provider.sendDocument(document)).thenReturn("accepted-document");

        scheduler.sendDueOutgoing();

        assertThat(document.getAccessPointDetails()).isNull();
        assertThat(document.getAccessPointId()).isEqualTo("accepted-document");
        assertThat(document.getAccessPoint()).isEqualTo(AccessPoint.RECOMMAND);
        assertThat(document.getProcessedStatus()).isNull();
        assertThat(balance.get()).isEqualTo(-1);
    }

    @Test
    void authorizationFailureDoesNotBlockTheNextCompanyInTheBatch() {
        UblDocument other = outgoing("0208:0123456789");
        when(documents.findAllByDirectionAndScheduledOnBeforeAndAccessPointIsNull(
                eq(DocumentDirection.OUTGOING), any(), any())).thenReturn(List.of(document, other));
        when(provider.sendDocument(document)).thenThrow(new SendAuthorizationException(false, null));
        when(provider.sendDocument(other)).thenReturn("other-company-document");

        scheduler.sendDueOutgoing();

        assertThat(document.getAccessPoint()).isNull();
        assertThat(other.getAccessPointId()).isEqualTo("other-company-document");
        verify(registry, never()).markNotRegistered(any(), any());
    }

    @Test
    void changedProviderDoesNotInheritExpiredAuthorizationFailures() {
        document.setAccessPointDetails(retryDetails(Instant.now().minus(Duration.ofDays(2)), 2));
        when(registry.getAccessPoint(document.getOwnerPeppolId())).thenReturn(AccessPoint.SCRADA);
        when(providers.get(AccessPoint.SCRADA)).thenReturn(provider);
        when(provider.sendDocument(document)).thenReturn("scrada-document");

        scheduler.sendDueOutgoing();

        assertThat(document.getAccessPoint()).isEqualTo(AccessPoint.SCRADA);
        assertThat(document.getAccessPointId()).isEqualTo("scrada-document");
        assertThat(document.getAccessPointDetails()).isNull();
    }

    private UblDocumentSchedulerService newScheduler() {
        var result = new UblDocumentSchedulerService(documents, registry, providers, backups, balance);
        ReflectionTestUtils.setField(result, "sendSlowDownFactor", 2);
        return result;
    }

    private UblDocument outgoing(String sender) {
        var result = new UblDocument();
        result.setId(UUID.randomUUID());
        result.setOwnerPeppolId(sender);
        result.setPartnerPeppolId("0208:1023290711");
        result.setDirection(DocumentDirection.OUTGOING);
        result.setScheduledOn(Instant.now().minusSeconds(1));
        result.setDownloadCount(0);
        return result;
    }

    private Map<String, Object> retryDetails(Instant firstFailure, int attempts) {
        return Map.of("sendAuthorizationProvider", "RECOMMAND",
                "sendAuthorizationFirstFailureOn", firstFailure.toString(),
                "sendAuthorizationAttempts", attempts);
    }
}

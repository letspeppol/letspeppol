package org.letspeppol.proxy.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.letspeppol.proxy.config.PhossConfig;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.exception.SendDocumentException;
import org.letspeppol.proxy.model.*;
import org.letspeppol.proxy.repository.RegistryRepository;
import org.letspeppol.proxy.util.PhossEnvelope;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PhossServiceTest {
    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> tokens = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private int responseCode;
    private String responseBody;
    private String base;
    private PhossService service;
    private PhossSubmissionStore store;
    private UblDocument document;
    private PhossSubmissionStore.Submission saved;
    private Path tokenFile;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            tokens.add(Objects.toString(exchange.getRequestHeaders().getFirst("X-Token"), ""));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] bytes = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        tokenFile = Files.createTempFile("phoss-test-token", ".txt");
        Files.writeString(tokenFile, "test-server-secret\n");
        WebClient client = new PhossConfig().phossApWebClient(base, tokenFile.toString());
        store = mock(PhossSubmissionStore.class);
        RegistryRepository registries = mock(RegistryRepository.class);
        when(registries.findById("9915:sender")).thenReturn(Optional.of(new Registry("9915:sender", AccessPoint.PHOSS,
                Map.of("c1Country", "BE"))));
        service = new PhossService(client, store, registries, new ObjectMapper(),
                new PhossSmpClient(new PhossConfig().phossSmpWebClient(base)));
        document = invoice();
        saved = new PhossSubmissionStore.Submission(document.getId(), "test-instance", "fingerprint", Instant.now(), false, null, null);
        when(store.prepare(eq(document.getId()), anyString())).thenReturn(saved);
        when(store.claim(document.getId())).thenReturn(true);
        when(store.load(document.getId())).thenReturn(saved);
        responseCode = 200;
        responseBody = "{}";
    }

    @AfterEach
    void cleanup() throws Exception { server.stop(0); Files.deleteIfExists(tokenFile); }

    public static UblDocument invoice() {
        UblDocument d = new UblDocument();
        d.setId(UUID.randomUUID());
        d.setCreatedOn(Instant.now());
        d.setType(DocumentType.INVOICE);
        d.setDirection(DocumentDirection.OUTGOING);
        d.setOwnerPeppolId("9915:sender");
        d.setPartnerPeppolId("9915:receiver");
        d.setUbl("""
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                 xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"
                 xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2">
                 <cbc:CustomizationID>%s</cbc:CustomizationID><cbc:ProfileID>%s</cbc:ProfileID>
                 <cbc:ID>example &amp; special</cbc:ID>
                 <cac:AccountingSupplierParty><cac:Party><cbc:EndpointID schemeID="9915">sender</cbc:EndpointID></cac:Party></cac:AccountingSupplierParty>
                 <cac:AccountingCustomerParty><cac:Party><cbc:EndpointID schemeID="9915">receiver</cbc:EndpointID></cac:Party></cac:AccountingCustomerParty>
                </Invoice>
                """.formatted(PhossEnvelope.CUSTOMIZATION, PhossEnvelope.PROCESS_ID));
        return d;
    }

    @Test
    void sendsEnvelopeWithDurableIdentityAndAuthenticatedServerSideClient() throws Exception {
        responseBody = "{\"sbdhInstanceIdentifier\":\"test-instance\",\"as4MessageId\":\"message@ap\",\"overallSuccess\":true}";
        assertThat(service.sendDocument(document)).isEqualTo("test-instance");
        assertThat(requests).containsExactly("POST /api/outbound/submit-sbd");
        assertThat(tokens).containsExactly("test-server-secret");
        var envelope = PhossEnvelope.parse(bodies.getFirst());
        assertThat(envelope.getElementsByTagNameNS(PhossEnvelope.SBD, "InstanceIdentifier").item(0).getTextContent()).isEqualTo("test-instance");
        assertThat(envelope.getElementsByTagNameNS(PhossEnvelope.INVOICE_NS, "Invoice").getLength()).isEqualTo(1);
        assertThat(bodies.getFirst()).doesNotContain("test-server-secret").contains("example &amp; special");
        var order = inOrder(store);
        order.verify(store).prepare(eq(document.getId()), anyString());
        order.verify(store).claim(document.getId());
        order.verify(store).report(eq(document.getId()), contains("message@ap"));
    }

    @Test
    void doesNotResubmitAfterAnAttemptEvenWhenStatusIs404() {
        when(store.claim(document.getId())).thenReturn(false);
        assertThat(service.sendDocument(document)).isEqualTo("test-instance");
        assertThat(requests).isEmpty();
        responseCode = 404;
        assertThat(service.getStatus(document)).isNull();
        service.sendDocument(document);
        assertThat(requests).hasSize(1).allMatch(r -> r.startsWith("GET "));
    }

    @Test
    void uncertainServerErrorIsReconciledNotRetried() {
        responseCode = 503;
        assertThat(service.sendDocument(document)).isEqualTo("test-instance");
        verify(store, never()).fail(any(), any());
    }

    @Test
    void transportConnectionFailureIsReconciledNotRetried() {
        server.stop(0);
        assertThat(service.sendDocument(document)).isEqualTo("test-instance");
        verify(store, never()).fail(any(), any());
    }

    @Test
    void retryable422IsPersistedNotMarkedTerminal() {
        responseCode = 422;
        responseBody = "{\"sbdhInstanceIdentifier\":\"test-instance\",\"sendingResult\":\"FAILURE\"}";
        assertThat(service.sendDocument(document)).isEqualTo("test-instance");
        verify(store).report(eq(document.getId()), contains("FAILURE"));
        verify(store, never()).fail(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404})
    void terminalSubmissionErrorsDoNotLeakProviderResponse(int code) {
        responseCode = code;
        responseBody = "secret diagnostics from provider";
        assertThatThrownBy(() -> service.sendDocument(document)).isInstanceOf(SendDocumentException.class)
                .hasMessageContaining("HTTP " + code).hasMessageNotContaining("secret");
        verify(store).fail(eq(document.getId()), contains("HTTP " + code));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "sending", "recovering", "failed", "new-state"})
    void unfinishedOrUnknownStatusesAreNotSuccess(String status) {
        responseBody = "{\"sbdhInstanceID\":\"test-instance\",\"status\":\"" + status + "\"}";
        assertThat(service.getStatus(document)).isNull();
        assertThat(requests).containsExactly("GET /api/outbound/status/test-instance?includeArchive=true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rejected", "permanently_failed"})
    void terminalStatusIsFailure(String status) {
        responseBody = "{\"sbdhInstanceID\":\"test-instance\",\"status\":\"" + status + "\"}";
        assertThat(service.getStatus(document).success()).isFalse();
    }

    @Test
    void sentStatusIsSuccessWithoutExposingRawReceipt() {
        responseBody = "{\"sbdhInstanceID\":\"test-instance\",\"status\":\"sent\"}";
        assertThat(service.getStatus(document).success()).isTrue();
        assertThat(document.getAccessPointDetails()).containsEntry("sbdhInstanceId", "test-instance")
                .doesNotContainKey("as4ReceivedSignalMsg");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{}", "{\"status\":\"sent\",\"sbdhInstanceID\":\"wrong\"}"})
    void malformedOrUncorrelatedStatusIsNotSuccess(String body) {
        responseBody = body;
        assertThat(service.getStatus(document)).isNull();
    }

    @Test
    void registrationIsLocalSendOnlyAndDoesNotWriteToSmp() {
        var registry = new Registry("9915:sender", AccessPoint.NONE, null);
        service.register(registry, new RegistrationRequest("Local sender", "en", "BE"));
        assertThat(registry.getReceiveDocuments()).isFalse();
        assertThat(registry.getVariables()).containsEntry("registrationMode", "send-only");
        service.unregister(registry.getPeppolId(), registry.getVariables());
        assertThat(requests).isEmpty();
        assertThatThrownBy(() -> service.receiveDocument(document)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void smpUsesEncodedIdentifiersWithoutApToken() {
        responseBody = "<SignedServiceMetadata/>";
        var smp = new PhossSmpClient(new PhossConfig().phossSmpWebClient(base));
        assertThat(smp.getInvoiceMetadata("9915:receiver")).isEqualTo(responseBody);
        assertThat(tokens).containsExactly("");
        assertThat(requests.getFirst()).contains("iso6523-actorid-upis%3A%3A9915%3Areceiver/services/")
                .contains("%23%23").doesNotContain("%253A");
        responseCode = 404;
        assertThat(smp.getInvoiceMetadata("9915:receiver")).isNull();
        responseCode = 503;
        assertThatThrownBy(() -> smp.getInvoiceMetadata("9915:receiver"))
                .isInstanceOf(IllegalStateException.class).hasMessage("PHOSS SMP lookup unavailable");
    }

    @Test
    void participantAvailabilityUsesReadOnlySmpWithoutCredentialsOrDockerUrlsInResponse() {
        responseBody = "<SignedServiceMetadata/>";
        assertThat(service.getReceivingAvailability("9915:receiver").receivingSmpUrl())
                .isEqualTo("Configured PHOSS SMP");
        assertThat(tokens).containsExactly("");
        responseCode = 404;
        assertThat(service.getReceivingAvailability("9915:receiver").receivingSmpUrl()).isNull();
    }

    @Test
    void rejectsDifferentPartiesUnsupportedTypesAndUnsafeXmlBeforeHttp() {
        document.setPartnerPeppolId("9915:wrong");
        assertThatThrownBy(() -> service.sendDocument(document)).isInstanceOf(SendDocumentException.class);
        document = invoice();
        document.setType(DocumentType.CREDIT_NOTE);
        assertThatThrownBy(() -> service.sendDocument(document)).isInstanceOf(SendDocumentException.class);
        document = invoice();
        document.setUbl("<!DOCTYPE Invoice [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" + document.getUbl());
        assertThatThrownBy(() -> service.sendDocument(document)).isInstanceOf(SendDocumentException.class);
        assertThat(requests).isEmpty();
        verify(store, never()).claim(any());
    }
}

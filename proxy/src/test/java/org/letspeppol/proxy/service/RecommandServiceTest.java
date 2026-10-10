package org.letspeppol.proxy.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.proxy.config.RecommandConfig;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.StatusReport;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.DocumentType;
import org.letspeppol.proxy.model.Registry;
import org.letspeppol.proxy.model.UblDocument;
import org.letspeppol.proxy.repository.RegistryRepository;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommandServiceTest {

    private static final byte[] SIGNED_CONTRACT = "%PDF-1.7\nSigned contract\n%%EOF".getBytes(StandardCharsets.UTF_8);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final List<Response> responses = new ArrayList<>();
    private HttpServer server;
    private RecommandService service;
    private UblDocumentReceiverService receiverService;
    private RegistryRepository registryRepository;
    private Counter registerCounter;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();

        receiverService = mock(UblDocumentReceiverService.class);
        registryRepository = mock(RegistryRepository.class);
        var webClient = new RecommandConfig().recommandWebClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "api-key",
                "api-secret"
        );
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        registerCounter = meterRegistry.counter("register");
        Counter unregisterCounter = meterRegistry.counter("unregister");
        Counter sendCounter = meterRegistry.counter("send");
        service = new RecommandService(
                receiverService,
                registryRepository,
                webClient,
                objectMapper,
                registerCounter,
                unregisterCounter,
                sendCounter
        );
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void registersAndUnregistersACompany() throws Exception {
        respond(200, """
                {"success":true,"company":{"id":"company-1"},"verificationUrl":"https://verify.example/1"}
                """);
        respond(200, "{\"success\":true}");
        respond(200, "{\"success\":true}");

        Map<String, Object> variables = service.register(
                "0208:0685912734",
                new RegistrationRequest(
                        "Example BV",
                        "NL",
                        "BE",
                        "Main Street 1",
                        "1000",
                        "Brussels",
                        "BE0685912734",
                        SIGNED_CONTRACT
                )
        );
        service.unregister("0208:0685912734", variables);

        assertThat(variables).containsEntry("companyId", "company-1")
                .containsEntry("verificationUrl", "https://verify.example/1");
        assertThat(requests).hasSize(3);
        assertThat(requests.get(0).method()).isEqualTo("POST");
        assertThat(requests.get(0).path()).isEqualTo("/api/v1/companies");
        assertThat(requests.get(0).authorization()).isEqualTo("Basic YXBpLWtleTphcGktc2VjcmV0");
        JsonNode createRequest = objectMapper.readTree(requests.get(0).body());
        assertThat(createRequest.path("enterpriseNumberScheme").asText()).isEqualTo("0208");
        assertThat(createRequest.path("enterpriseNumber").asText()).isEqualTo("0685912734");
        assertThat(createRequest.path("address").asText()).isEqualTo("Main Street 1");
        assertThat(createRequest.path("isSmpRecipient").asBoolean()).isTrue();
        Request verification = requests.get(1);
        assertThat(verification.method()).isEqualTo("POST");
        assertThat(verification.path()).isEqualTo("/api/v1/companies/company-1/verify-by-contract");
        assertThat(verification.authorization()).isEqualTo("Basic YXBpLWtleTphcGktc2VjcmV0");
        assertThat(verification.contentType()).startsWith("multipart/form-data;boundary=");
        assertThat(verification.body()).contains("name=\"contract\"", "filename=\"contract_signed.pdf\"",
                "Content-Type: application/pdf");
        assertThat(verification.bodyBytes()).containsSubsequence(SIGNED_CONTRACT);
        assertThat(registerCounter.count()).isEqualTo(1);
        assertThat(requests.get(2).method()).isEqualTo("DELETE");
        assertThat(requests.get(2).path()).isEqualTo("/api/v1/companies/company-1");
    }

    @Test
    void requiresASignedContractBeforeCreatingACompany() {
        assertThatThrownBy(() -> service.register("0208:0685912734", new RegistrationRequest("Example BV", "EN", "BE")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("signed PDF contract");
        assertThat(requests).isEmpty();
        assertThat(registerCounter.count()).isZero();
    }

    @Test
    void failsRegistrationAndRemovesTheCreatedCompanyWhenVerificationIsRefused() {
        respond(200, "{\"success\":true,\"company\":{\"id\":\"company-1\"}}");
        respond(400, "{\"success\":false,\"errors\":{\"authorization\":[\"Not allowed\"]}}");
        respond(200, "{\"success\":true}");

        assertThatThrownBy(() -> service.register("0208:0685912734", registrationWithContract()))
                .hasStackTraceContaining("verify company by contract").hasStackTraceContaining("400");
        assertThat(requests).extracting(Request::method, Request::path).containsExactly(
                org.assertj.core.groups.Tuple.tuple("POST", "/api/v1/companies"),
                org.assertj.core.groups.Tuple.tuple("POST", "/api/v1/companies/company-1/verify-by-contract"),
                org.assertj.core.groups.Tuple.tuple("DELETE", "/api/v1/companies/company-1")
        );
        assertThat(registerCounter.count()).isZero();
    }

    @Test
    void rejectsAnUnsuccessfulVerificationResponse() {
        respond(200, "{\"success\":true,\"company\":{\"id\":\"company-1\"}}");
        respond(200, "{\"success\":false}");
        respond(200, "{\"success\":true}");

        assertThatThrownBy(() -> service.register("0208:0685912734", registrationWithContract()))
                .hasStackTraceContaining("Recommand did not verify the company by contract");
        assertThat(requests.get(2).method()).isEqualTo("DELETE");
        assertThat(registerCounter.count()).isZero();
    }

    @Test
    void rejectsAnEmptyVerificationResponse() {
        respond(200, "{\"success\":true,\"company\":{\"id\":\"company-1\"}}");
        respond(200, "");
        respond(200, "{\"success\":true}");

        assertThatThrownBy(() -> service.register("0208:0685912734", registrationWithContract()))
                .hasStackTraceContaining("Empty response from Recommand verify company by contract");
        assertThat(registerCounter.count()).isZero();
    }

    private RegistrationRequest registrationWithContract() {
        return new RegistrationRequest("Example BV", "EN", "BE", "Main Street 1", "1000", "Brussels",
                "BE0685912734", SIGNED_CONTRACT);
    }

    @Test
    void sendsRawXmlAndReadsDeliveryStatus() {
        when(registryRepository.findById("0208:0685912734")).thenReturn(Optional.of(new Registry(
                "0208:0685912734",
                AccessPoint.RECOMMAND,
                Map.of("companyId", "company-1")
        )));
        respond(200, "{\"success\":true,\"sentOverPeppol\":true,\"id\":\"document-1\"}");
        respond(200, """
                {"success":true,"document":{"id":"document-1","sentOverPeppol":true,
                "peppolMessageId":"message-1","xml":"<Invoice/>","parsed":{"invoiceNumber":"INV-1"}}}
                """);

        UblDocument document = new UblDocument();
        document.setOwnerPeppolId("0208:0685912734");
        document.setPartnerPeppolId("0208:0123456789");
        document.setUbl("<Invoice/>");

        assertThat(service.sendDocument(document)).isEqualTo("document-1");
        document.setAccessPointId("document-1");
        StatusReport status = service.getStatus(document);

        assertThat(status.success()).isTrue();
        assertThat(document.getAccessPointDetails())
                .containsEntry("peppolMessageId", "message-1")
                .doesNotContainKeys("xml", "parsed");
        JsonNode sendRequest = readBody(requests.get(0));
        assertThat(sendRequest.path("recipient").asText()).isEqualTo("0208:0123456789");
        assertThat(sendRequest.path("documentType").asText()).isEqualTo("xml");
        assertThat(sendRequest.path("document").asText()).isEqualTo("<Invoice/>");
        assertThat(requests.get(1).path()).isEqualTo("/api/v1/documents/document-1");
    }

    @Test
    void receivesUnreadDocumentsAndMarksThemAsReadAfterStorage() {
        respond(200, """
                {"success":true,"documents":[{"id":"incoming-1","direction":"incoming",
                "senderId":"0208:0123456789","receiverId":"0208:0685912734","type":"invoice","readAt":null}]}
                """);
        respond(200, """
                {"success":true,"document":{"id":"incoming-1","direction":"incoming",
                "senderId":"0208:0123456789","receiverId":"0208:0685912734","type":"invoice",
                "docTypeId":"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2","xml":"<Invoice/>"}}
                """);
        respond(200, "{\"success\":true}");
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(6).run();
            return null;
        }).when(receiverService).createAsReceived(
                any(), any(), any(), any(), any(), any(), any()
        );

        service.receiveDocuments();

        verify(receiverService).createAsReceived(
                eq(DocumentType.INVOICE),
                eq("0208:0123456789"),
                eq("0208:0685912734"),
                eq("<Invoice/>"),
                eq(AccessPoint.RECOMMAND),
                eq("incoming-1"),
                any()
        );
        assertThat(requests).extracting(Request::method, Request::path).containsExactly(
                org.assertj.core.groups.Tuple.tuple("GET", "/api/v1/inbox"),
                org.assertj.core.groups.Tuple.tuple("GET", "/api/v1/documents/incoming-1"),
                org.assertj.core.groups.Tuple.tuple("POST", "/api/v1/documents/incoming-1/mark-as-read")
        );
        assertThat(readBody(requests.get(2)).path("read").asBoolean()).isTrue();
    }

    private JsonNode readBody(Request request) {
        try {
            return objectMapper.readTree(request.body());
        } catch (tools.jackson.core.JacksonException e) {
            throw new AssertionError(e);
        }
    }

    private void respond(int status, String body) {
        responses.add(new Response(status, body));
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        requests.add(new Request(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                body,
                exchange.getRequestHeaders().getFirst("Content-Type"),
                bodyBytes
        ));
        Response response = responses.remove(0);
        byte[] responseBody = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private record Request(String method, String path, String authorization, String body,
                           String contentType, byte[] bodyBytes) {}

    private record Response(int status, String body) {}
}

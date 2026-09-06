package org.letspeppol.proxy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommandServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final List<Response> responses = new ArrayList<>();
    private HttpServer server;
    private RecommandService service;
    private UblDocumentReceiverService receiverService;
    private RegistryRepository registryRepository;

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
        Counter registerCounter = meterRegistry.counter("register");
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

        Map<String, Object> variables = service.register(
                "0208:0685912734",
                new RegistrationRequest(
                        "Example BV",
                        "NL",
                        "BE",
                        "Main Street 1",
                        "1000",
                        "Brussels",
                        "BE0685912734"
                )
        );
        service.unregister("0208:0685912734", variables);

        assertThat(variables).containsEntry("companyId", "company-1")
                .containsEntry("verificationUrl", "https://verify.example/1");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).method()).isEqualTo("POST");
        assertThat(requests.get(0).path()).isEqualTo("/api/v1/companies");
        assertThat(requests.get(0).authorization()).isEqualTo("Basic YXBpLWtleTphcGktc2VjcmV0");
        JsonNode createRequest = objectMapper.readTree(requests.get(0).body());
        assertThat(createRequest.path("enterpriseNumberScheme").asText()).isEqualTo("0208");
        assertThat(createRequest.path("enterpriseNumber").asText()).isEqualTo("0685912734");
        assertThat(createRequest.path("address").asText()).isEqualTo("Main Street 1");
        assertThat(createRequest.path("isSmpRecipient").asBoolean()).isTrue();
        assertThat(requests.get(1).method()).isEqualTo("DELETE");
        assertThat(requests.get(1).path()).isEqualTo("/api/v1/companies/company-1");
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
        ReflectionTestUtils.setField(service, "receiveEnabled", true);
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
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void respond(int status, String body) {
        responses.add(new Response(status, body));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                body
        ));
        Response response = responses.remove(0);
        byte[] responseBody = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(response.status(), responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private record Request(String method, String path, String authorization, String body) {}

    private record Response(int status, String body) {}
}

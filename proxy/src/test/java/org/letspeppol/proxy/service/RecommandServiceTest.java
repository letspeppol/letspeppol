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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.letspeppol.proxy.exception.SendAuthorizationException;
import org.letspeppol.proxy.exception.AlreadyRegisteredException;
import org.letspeppol.proxy.dto.PeppolSettings;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
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
import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void registersOnly0208WithAllFourDocumentTypesAndKeepsVatData() {
        respondRegistrationSetup();
        respond(200, "{\"success\":true,\"isValid\":false}");
        respond(200, "{\"success\":true}");
        var registry = new Registry(ID, AccessPoint.RECOMMAND, null);
        service.register(registry, registrationWithContract());

        assertThat(service.getSettings(registry)).isEqualTo(new PeppolSettings(true));
        assertThat(requests).hasSize(9);
        assertThat(registry.getVariables()).containsEntry("companyId", "company-1").doesNotContainKey("companySettings");
        JsonNode create = readBody(requests.get(0));
        assertThat(create.path("skipDefaultCompanySetup").asBoolean()).isTrue();
        assertThat(create.path("isSmpRecipient").asBoolean()).isFalse();
        assertThat(create.path("vatNumber").asText()).isEqualTo("BE0685912734");
        assertThat(readBody(requests.get(1)).path("scheme").asText()).isEqualTo("0208");
        assertThat(readBody(requests.get(1)).path("identifier").asText()).isEqualTo("0685912734");
        assertThat(requests.stream().filter(r -> r.path().endsWith("/document-types")))
                .extracting(r -> readBody(r).path("docTypeId").asText()).containsExactlyElementsOf(REQUIRED_TYPES);
        assertThat(readBody(requests.get(7)).path("peppolAddress").asText()).isEqualTo(ID);
        assertThat(readBody(requests.get(8)).path("isSmpRecipient").asBoolean()).isTrue();
        assertThat(requests.get(6).bodyBytes()).containsSubsequence(SIGNED_CONTRACT);
        assertThat(requests).allSatisfy(r -> assertThat(r.body()).doesNotContain("9925"));
        assertThat(registerCounter.count()).isEqualTo(1);
        respond(200, "{\"success\":true}");
        service.unregister(ID, registry.getVariables());
        assertThat(requests.get(9).method()).isEqualTo("DELETE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"success\":true,\"isValid\":true}", "{}"})
    void registersSendOnlyWhen0208IsOccupiedOrLookupIsUncertain(String lookup) {
        respondRegistrationSetup();
        respond(200, lookup);
        var registry = new Registry(ID, AccessPoint.RECOMMAND, null);
        service.register(registry, registrationWithContract());

        assertThat(service.getSettings(registry)).isEqualTo(new PeppolSettings(false));
        assertThat(requests).hasSize(8);
        assertThat(requests.stream().filter(r -> r.method().equals("PUT"))).isEmpty();
        assertThat(registry.getVariables()).containsEntry("companyId", "company-1").doesNotContainKey("companySettings");
    }

    @Test
    void receivingConflictAfterLookupKeepsTheVerifiedCompanySendOnly() {
        respondRegistrationSetup();
        respond(200, "{\"success\":true,\"isValid\":false}");
        respond(409, "{}");

        var registry = new Registry(ID, AccessPoint.RECOMMAND, null);
        service.register(registry, registrationWithContract());
        assertThat(service.getSettings(registry))
                .isEqualTo(new PeppolSettings(false));
        assertThat(requests).extracting(Request::method).doesNotContain("DELETE");
    }

    @Test
    void validatesRequirementsBeforeRemoteChanges() {
        assertThatThrownBy(() -> service.register(new Registry(ID, AccessPoint.RECOMMAND, null), new RegistrationRequest("Example", "EN", "BE")))
                .hasMessageContaining("signed PDF contract");
        assertThatThrownBy(() -> service.validateRegistration("9925:BE0685912734", registrationWithContract()))
                .hasMessageContaining("0208");
        assertThatThrownBy(() -> service.register(new Registry("invalid-id", AccessPoint.RECOMMAND, null), registrationWithContract()))
                .hasMessageContaining("Peppol ID");
        assertThat(requests).isEmpty();
    }

    @Test
    void failedSetupDeletesTheNewCompanySoRegistrationCanBeRetried() {
        respond(200, "{\"success\":true,\"company\":{\"id\":\"company-1\"}}");
        respond(503, "{}");
        respond(200, "{\"success\":true}");

        assertThatThrownBy(() -> service.register(new Registry(ID, AccessPoint.RECOMMAND, null), registrationWithContract())).hasStackTraceContaining("set up company receiving");
        assertThat(requests).extracting(Request::method).containsExactly("POST", "POST", "DELETE");
        assertThat(registerCounter.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"success\":false}", ""})
    void failedContractVerificationDeletesTheNewCompany(String verification) {
        respond(200, "{\"success\":true,\"company\":{\"id\":\"company-1\"}}");
        respond(200, "{\"success\":true}");
        for (String type : REQUIRED_TYPES) respondCreatedType(type);
        respond(200, verification);
        respond(200, "{\"success\":true}");

        assertThatThrownBy(() -> service.register(new Registry(ID, AccessPoint.RECOMMAND, null), registrationWithContract())).hasStackTraceContaining("verify company by contract");
        assertThat(requests.get(7).method()).isEqualTo("DELETE");
        assertThat(registerCounter.count()).isZero();
    }

    @Test
    void cachedReadsAndUnchangedSettingsMakeNoProviderRequests() {
        var registry = cachedRegistry(true);
        assertThat(service.getSettings(registry)).isEqualTo(new PeppolSettings(true));
        assertThat(service.updateSettings(registry, new PeppolSettings(true))).isEqualTo(new PeppolSettings(true));
        assertThat(requests).isEmpty();
    }

    @Test
    void disablingReceivingOnlySendsOnePutAndKeepsAllDocumentTypes() {
        var registry = cachedRegistry(true);
        respond(200, "{\"success\":true}");
        assertThat(service.updateSettings(registry, new PeppolSettings(false))).isEqualTo(new PeppolSettings(false));
        assertThat(service.getSettings(registry)).isEqualTo(new PeppolSettings(false));
        assertThat(registry.getVariables()).containsOnlyKeys("companyId");
        assertThat(requests).extracting(Request::method).containsExactly("PUT");
    }

    @Test
    void enablingReceivingChecksOnly0208() {
        var registry = cachedRegistry(false);
        respond(200, "{\"success\":true,\"isValid\":false}");
        respond(200, "{\"success\":true}");
        assertThat(service.updateSettings(registry, new PeppolSettings(true))).isEqualTo(new PeppolSettings(true));
        assertThat(requests).extracting(Request::method).containsExactly("POST", "PUT");
        assertThat(readBody(requests.get(0)).path("peppolAddress").asText()).isEqualTo(ID);
    }

    @Test
    void failedReceivingWriteReconcilesOnlyCompanyStateAndPreservesRegistrationVariables() {
        var registry = cachedRegistry(true);
        registry.setVariables(Map.of("companyId", "company-1", "verificationUrl", "https://example.test/verify"));
        var registrationVariables = registry.getVariables();
        respond(503, "{}");

        assertThatThrownBy(() -> service.updateSettings(registry, new PeppolSettings(false)))
                .isInstanceOf(org.springframework.web.reactive.function.client.WebClientResponseException.class);
        assertThat(registry.getReceiveDocuments()).isNull();
        respondCompany(false);

        assertThat(service.getSettings(registry)).isEqualTo(new PeppolSettings(false));
        assertThat(service.getSettings(registry)).isEqualTo(new PeppolSettings(false));
        assertThat(registry.getVariables()).isSameAs(registrationVariables);
        assertThat(requests).extracting(Request::method).containsExactly("PUT", "GET");
    }

    @Test
    void unknownReceivingStateIsFetchedOnce() {
        var registry = new Registry(ID, AccessPoint.RECOMMAND, companyVariables());
        respondCompany(false);
        assertThat(service.getSettings(registry)).isEqualTo(new PeppolSettings(false));
        assertThat(service.getSettings(registry)).isEqualTo(new PeppolSettings(false));
        assertThat(requests).hasSize(1);

    }

    @Test
    void failedReceivingWriteCommitsInvalidationThroughBothTransactionalServices() throws Exception {
        var registry = cachedRegistry(true);
        when(registryRepository.findByIdForSettings(ID)).thenReturn(Optional.of(registry));
        var dataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        var transactions = new DataSourceTransactionManager(dataSource);
        var providers = mock(AccessPointServiceRegistry.class);
        when(providers.get(AccessPoint.RECOMMAND)).thenReturn((RecommandService) transactionalProxy(service, transactions));
        var registryService = (RegistryService) transactionalProxy(new RegistryService(registryRepository, providers), transactions);
        respond(503, "{}");

        assertThatThrownBy(() -> registryService.updateSettings(ID, new PeppolSettings(false)))
                .isInstanceOf(org.springframework.web.reactive.function.client.WebClientResponseException.class);
        assertThat(registry.getReceiveDocuments()).isNull();
        assertThat(registry.getVariables()).containsOnlyKeys("companyId");
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    private static final String ID = "0208:0685912734";
    private static final List<String> REQUIRED_TYPES = List.of(RecommandService.INVOICE, RecommandService.CREDIT_NOTE,
            RecommandService.SELF_BILLING_INVOICE, RecommandService.SELF_BILLING_CREDIT_NOTE);

    private Object transactionalProxy(Object target, DataSourceTransactionManager transactions) {
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return factory.getProxy();
    }

    private RegistrationRequest registrationWithContract() {
        return new RegistrationRequest("Example BV", "EN", "BE", "Main Street 1", "1000", "Brussels", "BE0685912734", SIGNED_CONTRACT);
    }

    private Map<String, Object> companyVariables() {
        return new java.util.HashMap<>(Map.of("companyId", "company-1"));
    }

    private Registry cachedRegistry(boolean receiving) {
        var registry = new Registry(ID, AccessPoint.RECOMMAND, companyVariables());
        registry.setReceiveDocuments(receiving);
        return registry;
    }

    private List<Map<String, String>> typeRecords(List<String> types) {
        return types.stream().map(type -> Map.of("id", "type-" + REQUIRED_TYPES.indexOf(type), "docTypeId", type,
                "processId", type.contains("selfbilling") ? RecommandService.SELF_BILLING_PROCESS : RecommandService.BILLING_PROCESS)).toList();
    }

    private void respondCreatedType(String type) {
        respond(200, objectMapper.writeValueAsString(Map.of("success", true, "documentType", typeRecords(List.of(type)).getFirst())));
    }

    private void respondRegistrationSetup() {
        respond(200, "{\"success\":true,\"company\":{\"id\":\"company-1\"}}");
        respond(200, "{\"success\":true}");
        for (String type : REQUIRED_TYPES) respondCreatedType(type);
        respond(200, "{\"success\":true}");
    }

    private void respondCompany(boolean receiving) {
        respond(200, "{\"success\":true,\"company\":{\"id\":\"company-1\",\"isSmpRecipient\":" + receiving + ",\"vatNumber\":\"BE0685912734\"}}");
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 404, 401, 503})
    void classifiesUnauthorizedSendUsingCompanyLookup(int lookupStatus) {
        when(registryRepository.findById("0208:0685912734")).thenReturn(Optional.of(new Registry(
                "0208:0685912734", AccessPoint.RECOMMAND, Map.of("companyId", "company-1"))));
        respond(401, "{\"success\":false,\"errors\":{\"root\":[\"Unauthorized\"]}}");
        respond(lookupStatus, "{}");

        UblDocument document = new UblDocument();
        document.setOwnerPeppolId("0208:0685912734");
        document.setPartnerPeppolId("0208:0123456789");
        document.setUbl("<Invoice/>");

        assertThatThrownBy(() -> service.sendDocument(document))
                .isInstanceOfSatisfying(SendAuthorizationException.class,
                        failure -> assertThat(failure.isCompanyMissing()).isEqualTo(lookupStatus == 404));
        assertThat(requests).extracting(Request::method, Request::path).containsExactly(
                org.assertj.core.groups.Tuple.tuple("POST", "/api/v1/company-1/send"),
                org.assertj.core.groups.Tuple.tuple("GET", "/api/v1/companies/company-1"));
    }

    @Test
    void authorizationFailureDoesNotRollBackTheSchedulersTransaction() throws Exception {
        when(registryRepository.findById("0208:0685912734")).thenReturn(Optional.of(new Registry(
                "0208:0685912734", AccessPoint.RECOMMAND, Map.of("companyId", "company-1"))));
        respond(401, "{}");
        respond(404, "{}");
        UblDocument document = new UblDocument();
        document.setOwnerPeppolId("0208:0685912734");
        document.setPartnerPeppolId("0208:0123456789");
        document.setUbl("<Invoice/>");

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        var transactions = new DataSourceTransactionManager(dataSource);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        RecommandService transactionalService = (RecommandService) factory.getProxy();

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThatThrownBy(() -> transactionalService.sendDocument(document))
                    .isInstanceOf(SendAuthorizationException.class);
            assertThat(status.isRollbackOnly()).isFalse();
        });

        verify(connection).commit();
        verify(connection, never()).rollback();
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
        Response response = responses.isEmpty() ? new Response(500, "Unexpected provider request") : responses.remove(0);
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

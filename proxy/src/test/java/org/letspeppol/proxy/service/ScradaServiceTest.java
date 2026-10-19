package org.letspeppol.proxy.service;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.exception.AlreadyRegisteredException;
import org.letspeppol.proxy.exception.GlobalExceptionHandler;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.DocumentType;
import org.letspeppol.proxy.model.Registry;
import org.letspeppol.proxy.model.UblDocument;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ScradaServiceTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"errorCode\":110554,\"errorType\":1,\"defaultFormat\":\"Already registered\",\"parameters\":[\"0208:0123456789\",\"https://other-smp.example\"],\"innerErrors\":[]}",
            "{\"errorCode\":100008,\"errorType\":1,\"defaultFormat\":\"Registration failed\",\"innerErrors\":[{\"errorCode\":110554,\"errorType\":1,\"parameters\":[\"0208:0123456789\",\"https://other-smp.example\"]}]}",
            "{\"errorCode\":110554,\"defaultFormat\":\"Already registered\",\"parameters\":[\"0208:0123456789\",\"https://other-smp.example\"]}",
            "{\"errorCode\":100008,\"errorType\":null,\"innerErrors\":[{\"errorCode\":110554,\"errorType\":null,\"parameters\":[\"0208:0123456789\",\"https://other-smp.example\"]}]}"
    })
    void mapsScrada500RegistrationConflictsTo409WithTheProvider(String body) throws Exception {
        assertRegistrationConflict(body, "https://other-smp.example");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"errorCode\":110554}",
            "{\"errorCode\":110554,\"parameters\":null}",
            "{\"errorCode\":110554,\"parameters\":[]}",
            "{\"errorCode\":110554,\"parameters\":[\"0208:0123456789\"]}",
            "{\"errorCode\":110554,\"parameters\":[\"0208:0123456789\",null]}",
            "{\"errorCode\":110554,\"parameters\":[\"0208:0123456789\",\" \" ]}",
            "{\"errorCode\":100008,\"innerErrors\":[{\"errorCode\":110554,\"parameters\":null}]}"
    })
    void preservesRegistrationConflictsWhenScradaOmitsTheProvider(String body) throws Exception {
        assertRegistrationConflict(body, "another Peppol provider");
    }

    private void assertRegistrationConflict(String body, String expectedProvider) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/register", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var service = new ScradaService(mock(UblDocumentReceiverService.class),
                    WebClient.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    mock(Counter.class), mock(Counter.class), mock(Counter.class), mock(Counter.class), new JsonMapper());
            assertThatThrownBy(() -> service.register(new Registry("0208:0123456789", AccessPoint.NONE, null),
                    new RegistrationRequest("Example", "EN", "BE")))
                    .isInstanceOfSatisfying(AlreadyRegisteredException.class, conflict -> {
                        var response = new GlobalExceptionHandler().handleAlreadyRegisteredException(conflict);
                        assertThat(response.getStatusCode().value()).isEqualTo(409);
                        assertThat(response.getBody()).isEqualTo(expectedProvider);
                    });
        } finally {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @EnumSource(DocumentType.class)
    void sendsUploadedXmlWithTheCorrectDocumentAndProcessIdentifiers(DocumentType type) throws Exception {
        var receivedType = new AtomicReference<String>();
        var receivedProcess = new AtomicReference<String>();
        var receivedBody = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/outbound/document", exchange -> {
            receivedType.set(exchange.getRequestHeaders().getFirst("x-scrada-peppol-document-type-value"));
            receivedProcess.set(exchange.getRequestHeaders().getFirst("x-scrada-peppol-process-value"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "\"document-id\"".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var service = new ScradaService(mock(UblDocumentReceiverService.class),
                    WebClient.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    mock(Counter.class), mock(Counter.class), mock(Counter.class), mock(Counter.class), new JsonMapper());
            var document = new UblDocument();
            document.setId(UUID.randomUUID());
            document.setType(type);
            document.setOwnerPeppolId("0208:0123456789");
            document.setPartnerPeppolId("0208:9876543210");
            document.setUbl("<uploaded-document>Original XML</uploaded-document>");

            assertThat(service.sendDocument(document)).isEqualTo("document-id");
            String root = type.isInvoice() ? "Invoice" : "CreditNote";
            String profile = type.isSelfBilling() ? "selfbilling" : "billing";
            assertThat(receivedType.get()).isEqualTo("urn:oasis:names:specification:ubl:schema:xsd:" + root + "-2::" + root
                    + "##urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:" + profile + ":3.0::2.1");
            assertThat(receivedProcess.get()).isEqualTo("urn:fdc:peppol.eu:2017:poacc:" + profile + ":01:1.0");
            assertThat(receivedBody.get()).isEqualTo(document.getUbl());
        } finally {
            server.stop(0);
        }
    }
}

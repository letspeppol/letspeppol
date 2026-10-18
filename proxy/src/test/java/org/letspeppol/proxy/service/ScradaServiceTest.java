package org.letspeppol.proxy.service;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.letspeppol.proxy.model.DocumentType;
import org.letspeppol.proxy.model.UblDocument;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ScradaServiceTest {
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

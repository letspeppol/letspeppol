package org.letspeppol.app.service;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.DocumentRepository;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Executable proof for the background App -> Proxy document synchronization diagram. */
class DocumentNetworkFlowTest {

    @Test
    void backgroundSynchronizationPollsThenAcknowledgesProxyDocuments() {
        List<String> requests = new CopyOnWriteArrayList<>();
        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> {
                    requests.add(request.method().name() + " " + request.uri());
                    if ("GET".equals(request.method().name())) {
                        response.status(200).header("Content-Type", "application/json");
                        return response.sendString(Mono.just("[]"));
                    }
                    response.status(204);
                    return response.send();
                })
                .bindNow();

        try {
            WebClient proxy = WebClient.create("http://localhost:" + server.port());
            DocumentService service = new DocumentService(
                    mock(CompanyRepository.class),
                    mock(DocumentRepository.class),
                    mock(ValidationService.class),
                    mock(NotificationService.class),
                    mock(UblInvoicePdfService.class),
                    new com.fasterxml.jackson.databind.ObjectMapper(),
                    proxy,
                    proxy,
                    mock(Counter.class),
                    mock(Counter.class),
                    mock(Counter.class));

            service.periodicSynchronize();

            assertThat(requests).containsExactly(
                    "GET /sapi/document",
                    "PUT /sapi/document/downloaded");
        } finally {
            server.disposeNow();
        }
    }
}

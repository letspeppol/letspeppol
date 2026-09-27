package org.letspeppol.app.service;

import org.junit.jupiter.api.Test;
import org.letspeppol.app.controller.PeppolDirectoryController;
import org.letspeppol.app.repository.DocumentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Executable proof for public App -> Directory and App -> Proxy aggregate calls. */
class PublicApiNetworkFlowTest {

    @Test
    void participantLookupIsForwardedToPeppolDirectory() {
        AtomicReference<String> requestedUri = new AtomicReference<>();
        DisposableServer server = jsonServer(requestedUri, "{\"matches\":1}");
        try {
            PeppolDirectoryController controller = new PeppolDirectoryController();
            ReflectionTestUtils.setField(controller, "webClient",
                    WebClient.create("http://localhost:" + server.port()));

            ResponseEntity<String> response = controller.find(null, "0208:0123456789");

            assertThat(response.getBody()).isEqualTo("{\"matches\":1}");
            assertThat(requestedUri.get()).isEqualTo("/search/1.0/json?q=0208%3A0123456789");
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void publicStatisticsLoadsActiveCompanyCountFromProxy() {
        AtomicReference<String> requestedUri = new AtomicReference<>();
        DisposableServer server = jsonServer(requestedUri, "{\"activeCompanies\":42}");
        try {
            StatisticsService service = new StatisticsService(
                    mock(DocumentRepository.class),
                    WebClient.create("http://localhost:" + server.port()));

            assertThat(service.activeCompanies()).isEqualTo(42);
            assertThat(requestedUri.get()).isEqualTo("/api/stats");
        } finally {
            server.disposeNow();
        }
    }

    private static DisposableServer jsonServer(AtomicReference<String> requestedUri, String body) {
        return HttpServer.create()
                .port(0)
                .handle((request, response) -> {
                    requestedUri.set(request.uri());
                    response.status(200).header("Content-Type", "application/json");
                    return response.sendString(Mono.just(body));
                })
                .bindNow();
    }
}

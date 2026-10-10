package org.letspeppol.app.service;

import org.junit.jupiter.api.Test;
import org.letspeppol.app.controller.PeppolRegistrationController;
import org.letspeppol.app.dto.PeppolRegistrationDto;
import org.letspeppol.app.exception.SecurityException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeppolRegistrationNetworkFlowTest {

    @Test
    void readsTheJwtCompanyFromProxyUsingTheServiceClient() {
        AtomicReference<String> uri = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        DisposableServer server = HttpServer.create().port(0).handle((request, response) -> {
            uri.set(request.uri());
            authorization.set(request.requestHeaders().get("Authorization"));
            return response.header("Content-Type", "application/json").sendString(Mono.just("""
                    {"peppolId":"0208:0123456789","peppolActive":true,"accessPoint":"SCRADA"}
                    """));
        }).bindNow();

        try {
            WebClient client = WebClient.builder().baseUrl("http://localhost:" + server.port())
                    .defaultHeader("Authorization", "Bearer service-token").build();
            PeppolRegistrationController controller = new PeppolRegistrationController(new PeppolRegistrationService(client));
            Jwt jwt = Jwt.withTokenValue("user-token").header("alg", "RS256")
                    .claim("peppolId", "0208:0123456789").build();

            assertThat(controller.getRegistration(jwt))
                    .isEqualTo(new PeppolRegistrationDto("0208:0123456789", true, "SCRADA"));
            assertThat(uri.get()).isEqualTo("/sapi/registry?peppolId=0208:0123456789");
            assertThat(authorization.get()).isEqualTo("Bearer service-token");
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void missingRegistryEntryMeansNoRegistration() {
        DisposableServer server = HttpServer.create().port(0)
                .handle((request, response) -> response.status(404).send()).bindNow();
        try {
            PeppolRegistrationService service = new PeppolRegistrationService(WebClient.create("http://localhost:" + server.port()));
            assertThat(service.get("0208:0123456789"))
                    .isEqualTo(new PeppolRegistrationDto("0208:0123456789", false, "NONE"));
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void proxyFailureIsUnavailableRatherThanUnregistered() {
        DisposableServer server = HttpServer.create().port(0)
                .handle((request, response) -> response.status(500).send()).bindNow();
        try {
            PeppolRegistrationService service = new PeppolRegistrationService(WebClient.create("http://localhost:" + server.port()));
            assertThatThrownBy(() -> service.get("0208:0123456789"))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void jwtWithoutCompanyCannotQueryTheRegistry() {
        PeppolRegistrationController controller = new PeppolRegistrationController(
                new PeppolRegistrationService(WebClient.create("http://localhost:1")));
        Jwt jwt = Jwt.withTokenValue("user-token").header("alg", "RS256").subject("user").build();

        assertThatThrownBy(() -> controller.getRegistration(jwt)).isInstanceOf(SecurityException.class);
    }
}

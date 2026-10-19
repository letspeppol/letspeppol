package org.letspeppol.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.letspeppol.app.controller.PeppolRegistrationController;
import org.letspeppol.app.dto.PeppolRegistrationDto;
import org.letspeppol.app.dto.PeppolSettings;
import org.letspeppol.app.exception.SecurityException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PeppolRegistrationNetworkFlowTest {

    @Test
    void readsTheJwtCompanyFromProxyUsingTheServiceClient() {
        AtomicReference<String> uri = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        DisposableServer server = HttpServer.create().port(0).handle((request, response) -> {
            uri.set(request.uri());
            authorization.set(request.requestHeaders().get("Authorization"));
            return response.header("Content-Type", "application/json").sendString(Mono.just("""
                    {"peppolId":"0208:0123456789","peppolActive":true,"accessPoint":"SCRADA","defaultAccessPoint":"RECOMMAND"}
                    """));
        }).bindNow();

        try {
            WebClient client = WebClient.builder().baseUrl("http://localhost:" + server.port())
                    .defaultHeader("Authorization", "Bearer service-token").build();
            PeppolRegistrationController controller = new PeppolRegistrationController(new PeppolRegistrationService(client));
            Jwt jwt = Jwt.withTokenValue("user-token").header("alg", "RS256")
                    .claim("peppolId", "0208:0123456789").build();

            assertThat(controller.getRegistration(jwt))
                    .isEqualTo(new PeppolRegistrationDto("0208:0123456789", true, "SCRADA", "RECOMMAND"));
            assertThat(uri.get()).isEqualTo("/sapi/registry/registration-status?peppolId=0208:0123456789");
            assertThat(authorization.get()).isEqualTo("Bearer service-token");
        } finally {
            server.disposeNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECOMMAND", "SCRADA"})
    void unconnectedCompaniesIncludeTheDefaultAccessPoint(String defaultAccessPoint) {
        DisposableServer server = HttpServer.create().port(0)
                .handle((request, response) -> response.header("Content-Type", "application/json").sendString(Mono.just("""
                        {"peppolId":"0208:0123456789","peppolActive":false,"accessPoint":"NONE","defaultAccessPoint":"%s"}
                        """.formatted(defaultAccessPoint)))).bindNow();
        try {
            PeppolRegistrationService service = new PeppolRegistrationService(WebClient.create("http://localhost:" + server.port()));
            assertThat(service.get("0208:0123456789"))
                    .isEqualTo(new PeppolRegistrationDto("0208:0123456789", false, "NONE", defaultAccessPoint));
        } finally {
            server.disposeNow();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 500})
    void proxyFailureIsUnavailableRatherThanUnregistered(int status) {
        DisposableServer server = HttpServer.create().port(0)
                .handle((request, response) -> response.status(status).send()).bindNow();
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
        assertThatThrownBy(() -> controller.getReceivingAvailability(jwt)).isInstanceOf(SecurityException.class);
    }

    @Test
    void receivingAvailabilityUsesOnlyTheJwtCompanyAndServiceCredentials() throws Exception {
        AtomicReference<String> uri = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        DisposableServer server = HttpServer.create().port(0).handle((request, response) -> {
            uri.set(request.uri());
            authorization.set(request.requestHeaders().get("Authorization"));
            return response.header("Content-Type", "application/json").sendString(Mono.just(
                    "{\"receiveDocuments\":false,\"receivingSmpUrl\":\"https://other-smp.example\"}"));
        }).bindNow();
        try {
            var client = WebClient.builder().baseUrl("http://localhost:" + server.port())
                    .defaultHeader("Authorization", "Bearer service-token").build();
            var controller = new PeppolRegistrationController(new PeppolRegistrationService(client));
            Jwt jwt = Jwt.withTokenValue("user-token").header("alg", "RS256")
                    .claim("peppolId", "0208:0123456789").claim("accountType", "ADMIN").build();
            var mvc = MockMvcBuilders.standaloneSetup(controller)
                    .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                    .build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

            mvc.perform(get("/sapi/company/peppol-registration/receiving-availability")
                            .param("peppolId", "0208:9999999999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.receivingSmpUrl").value("https://other-smp.example"));
            assertThat(uri.get()).isEqualTo("/sapi/registry/receiving-availability?peppolId=0208:0123456789");
            assertThat(authorization.get()).isEqualTo("Bearer service-token");
        } finally {
            SecurityContextHolder.clearContext();
            server.disposeNow();
        }
    }

    @Test
    void receivingLookupFailuresAreUnavailable() {
        DisposableServer server = HttpServer.create().port(0)
                .handle((request, response) -> response.status(500).send()).bindNow();
        try {
            var service = new PeppolRegistrationService(WebClient.create("http://localhost:" + server.port()));
            assertThatThrownBy(() -> service.getReceivingAvailability("0208:0123456789"))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void settingsUseTheJwtCompanyAndServiceCredentials() {
        AtomicReference<String> uri = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        DisposableServer server = HttpServer.create().port(0).handle((request, response) -> {
            uri.set(request.uri());
            authorization.set(request.requestHeaders().get("Authorization"));
            return request.receive().aggregate().asString().defaultIfEmpty("").flatMap(content -> {
                body.set(content);
                return response.header("Content-Type", "application/json")
                        .sendString(Mono.just("{\"receiveDocuments\":false,\"receivingSmpUrl\":\"https://other-smp.example\"}")).then();
            });
        }).bindNow();
        try {
            WebClient client = WebClient.builder().baseUrl("http://localhost:" + server.port())
                    .defaultHeader("Authorization", "Bearer service-token").build();
            var controller = new PeppolRegistrationController(new PeppolRegistrationService(client));
            Jwt jwt = Jwt.withTokenValue("user-token").header("alg", "RS256")
                    .claim("peppolId", "0208:0123456789").claim("accountType", "ADMIN").build();

            assertThat(controller.getSettings(jwt)).isEqualTo(new PeppolSettings(false, "https://other-smp.example"));
            assertThat(controller.updateSettings(jwt, new PeppolSettings(false)))
                    .isEqualTo(new PeppolSettings(false, "https://other-smp.example"));
            assertThat(uri.get()).isEqualTo("/sapi/registry/settings?peppolId=0208:0123456789");
            assertThat(authorization.get()).isEqualTo("Bearer service-token");
            assertThat(body.get()).contains("\"receiveDocuments\":false");
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void onlyAdministratorsCanChangeSettings() {
        AtomicInteger calls = new AtomicInteger();
        DisposableServer server = HttpServer.create().port(0).handle((request, response) -> {
            calls.incrementAndGet();
            return response.status(200).send();
        }).bindNow();
        try {
            var controller = new PeppolRegistrationController(new PeppolRegistrationService(
                    WebClient.create("http://localhost:" + server.port())));
            for (String role : new String[]{"USER", "USER_READ", "USER_DRAFT", "APP", "ACCOUNTANT", ""}) {
                Jwt jwt = Jwt.withTokenValue("user-token").header("alg", "RS256")
                        .claim("peppolId", "0208:0123456789").claim("accountType", role).build();
                assertThatThrownBy(() -> controller.updateSettings(jwt, new PeppolSettings(true)))
                        .isInstanceOfSatisfying(ResponseStatusException.class,
                                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
            }
            assertThat(calls.get()).isZero();
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void reportsReceivingConflictsAndProviderOutagesWithoutClaimingAnUpdateSucceeded() {
        for (int status : new int[]{409, 500}) {
            DisposableServer server = HttpServer.create().port(0)
                    .handle((request, response) -> response.status(status).send()).bindNow();
            try {
                var service = new PeppolRegistrationService(WebClient.create("http://localhost:" + server.port()));
                assertThatThrownBy(() -> service.updateSettings("0208:0123456789", new PeppolSettings(true)))
                        .isInstanceOfSatisfying(ResponseStatusException.class,
                                ex -> assertThat(ex.getStatusCode()).isEqualTo(
                                        status == 409 ? HttpStatus.CONFLICT : HttpStatus.SERVICE_UNAVAILABLE));
            } finally {
                server.disposeNow();
            }
        }
    }
}

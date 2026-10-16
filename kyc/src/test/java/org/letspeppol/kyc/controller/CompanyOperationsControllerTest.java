package org.letspeppol.kyc.controller;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.letspeppol.kyc.config.JsonAwareAuthenticationFailureHandler;
import org.letspeppol.kyc.config.ProxyConfig;
import org.letspeppol.kyc.config.SecurityConfig;
import org.letspeppol.kyc.config.TotpAuthenticationSuccessHandler;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.OwnershipRepository;
import org.letspeppol.kyc.service.ContractStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CompanyOperationsController.class)
@Import(CompanyOperationsControllerTest.LocalConfig.class)
class CompanyOperationsControllerTest {
    private static final String ID = "0208:0685912734";
    private static final String PATH = "/lapi/company/" + ID + "/move";
    private static final byte[] CONTRACT = "%PDF-1.7\nSigned contract".getBytes(StandardCharsets.UTF_8);
    private static final List<ForwardedRequest> requests = new CopyOnWriteArrayList<>();
    private static HttpServer server;
    private static volatile int proxyStatus;
    private static volatile String proxyBody;
    @Autowired private MockMvc mvc;
    @MockitoBean private CompanyRepository companies;
    @MockitoBean private OwnershipRepository ownerships;
    @MockitoBean private ContractStorageService storage;
    private Company company;

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"access_token\":\"test-service-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/sapi/registry/", exchange -> {
            requests.add(new ForwardedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getQuery(), exchange.getRequestHeaders().getFirst("Authorization"),
                    new ObjectMapper().readTree(exchange.getRequestBody().readAllBytes())));
            byte[] body = proxyBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", proxyStatus == 400 ? "text/plain" : "application/json");
            exchange.sendResponseHeaders(proxyStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stop() {
        if (server != null) server.stop(0);
    }

    @BeforeEach
    void setUp() {
        requests.clear();
        proxyStatus = 200;
        proxyBody = "{\"peppolId\":\"" + ID + "\",\"peppolActive\":true,\"accessPoint\":\"RECOMMAND\"}";
        company = new Company(ID, "0685912734", "BE0685912734", "Example BV");
        company.setRegisteredOnPeppol(true);
        company.setAddress("Brussels", "1000", "Main Street 1");
        when(companies.findByPeppolId(ID)).thenReturn(Optional.of(company));
        var admin = new Account();
        admin.setId(42L);
        when(ownerships.findFirstByCompanyPeppolIdAndTypeOrderByLastUsedDesc(ID, AccountType.ADMIN))
                .thenReturn(Optional.of(new Ownership(admin, AccountType.ADMIN, company)));
        when(storage.getContract(ID, 42L)).thenReturn(CONTRACT);
    }

    @Test
    void operatorSuppliesOnlyIdAndTargetAndKycSendsItsStoredDataWithServiceCredentials() throws Exception {
        mvc.perform(post(PATH).param("accessPoint", "RECOMMAND"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessPoint").value("RECOMMAND"));

        assertThat(requests).hasSize(1);
        var request = requests.getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/sapi/registry/" + ID + "/move");
        assertThat(request.query()).isEqualTo("accessPoint=RECOMMAND");
        assertThat(request.authorization()).isEqualTo("Bearer test-service-token");
        assertThat(request.body().path("name").asText()).isEqualTo("Example BV");
        assertThat(request.body().path("address").asText()).isEqualTo("Main Street 1");
        assertThat(request.body().path("postalCode").asText()).isEqualTo("1000");
        assertThat(request.body().path("city").asText()).isEqualTo("Brussels");
        assertThat(request.body().path("vatNumber").asText()).isEqualTo("BE0685912734");
        assertThat(Base64.getDecoder().decode(request.body().path("signedContract").asText())).isEqualTo(CONTRACT);
        verify(companies).save(company);
    }

    @Test
    void omittedTargetIsLeftToTheProxyDefault() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isOk());
        assertThat(requests.getFirst().query()).isNull();
    }

    @Test
    void failedMigrationKeepsKycStateInSyncWithTheProxy() throws Exception {
        proxyStatus = 424;
        proxyBody = "{\"peppolActive\":false,\"accessPoint\":\"NONE\"}";
        mvc.perform(post(PATH)).andExpect(status().isFailedDependency())
                .andExpect(jsonPath("$.peppolActive").value(false));
        assertThat(company.isRegisteredOnPeppol()).isFalse();
        verify(companies).save(company);
    }

    @Test
    void restoredSourceRemainsActiveAndReportsTheFailedMigration() throws Exception {
        proxyStatus = 424;
        proxyBody = "{\"peppolActive\":true,\"accessPoint\":\"SCRADA\"}";
        mvc.perform(post(PATH)).andExpect(status().isFailedDependency())
                .andExpect(jsonPath("$.accessPoint").value("SCRADA"));
        assertThat(company.isRegisteredOnPeppol()).isTrue();
    }

    @Test
    void validationErrorsAreForwardedWithoutChangingKycState() throws Exception {
        proxyStatus = 400;
        proxyBody = "Invalid accessPoint parameter";
        mvc.perform(post(PATH).param("accessPoint", "INVALID")).andExpect(status().isBadRequest())
                .andExpect(content().string(proxyBody));
        verify(companies, never()).save(any());
    }

    @Test
    void suspendedCompanyCannotStartAMigration() throws Exception {
        company.setSuspended(true);
        mvc.perform(post(PATH)).andExpect(status().isForbidden());
        assertThat(requests).isEmpty();
        verifyNoInteractions(ownerships, storage);
    }

    @Test
    void missingContractStopsBeforeTheProxyCall() throws Exception {
        when(storage.getContract(ID, 42L)).thenThrow(new IllegalStateException("Failed to read signed contract"));
        mvc.perform(post(PATH)).andExpect(status().isInternalServerError());
        assertThat(requests).isEmpty();
    }

    @Test
    void registrationDataIsNoLongerExposedByALocalGetEndpoint() throws Exception {
        mvc.perform(get("/lapi/company/" + ID + "/registration")).andExpect(status().isNotFound());
    }

    private record ForwardedRequest(String method, String path, String query, String authorization, JsonNode body) {}

    @TestConfiguration
    @EnableWebSecurity
    static class LocalConfig {
        @Bean UserDetailsService users() { return mock(UserDetailsService.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean("ProxyWebClient")
        WebClient proxyWebClient() {
            var config = new ProxyConfig();
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            return config.webClient(url, config.clientRegistrationRepository("kyc-service", "test-secret", url + "/token"));
        }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return new SecurityConfig().browserAndPublicSecurityFilterChain(http, mock(JwtDecoder.class),
                    request -> new CorsConfiguration(), mock(TotpAuthenticationSuccessHandler.class),
                    mock(JsonAwareAuthenticationFailureHandler.class));
        }
    }
}

package org.letspeppol.kyc.service;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.OwnershipRepository;
import org.letspeppol.kyc.service.kbo.KboLookupService;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyRegistrationContractTest {

    private static final byte[] CONTRACT = "%PDF-1.7\nSigned contract\n%%EOF".getBytes(StandardCharsets.UTF_8);
    @TempDir Path dataDirectory;
    private HttpServer server;
    private CompanyService service;
    private ContractStorageService storage;
    private CompanyRepository companyRepository;
    private Company company;
    private volatile JsonNode registrationBody;
    private volatile String registrationPath;
    private int responseStatus = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            registrationBody = new ObjectMapper().readTree(exchange.getRequestBody().readAllBytes());
            registrationPath = exchange.getRequestURI().toString();
            byte[] body = "{\"peppolActive\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var proxyService = new ProxyService(WebClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build());
        storage = new ContractStorageService(dataDirectory.toString());
        companyRepository = mock(CompanyRepository.class);
        var ownershipRepository = mock(OwnershipRepository.class);
        company = new Company("0208:0685912734", "0685912734", "BE0685912734", "Example BV");
        Account admin = new Account();
        admin.setId(42L);
        when(ownershipRepository.findFirstByCompanyPeppolIdAndTypeOrderByLastUsedDesc(company.getPeppolId(), AccountType.ADMIN))
                .thenReturn(Optional.of(new Ownership(admin, AccountType.ADMIN, company)));
        service = new CompanyService(companyRepository, ownershipRepository, mock(KboLookupService.class),
                proxyService, storage, mock(Counter.class));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void forwardsTheStoredAdminContractDuringRegistrationAndReregistration() {
        storage.storeContract(company.getPeppolId(), 42L, CONTRACT);

        assertThat(service.registerCompany(company).peppolActive()).isTrue();

        assertThat(registrationPath).isEqualTo("/sapi/registry?peppolId=0208:0685912734");
        assertThat(Base64.getDecoder().decode(registrationBody.path("signedContract").asText())).isEqualTo(CONTRACT);
        assertThat(registrationBody.path("name").asText()).isEqualTo("Example BV");
        assertThat(company.isRegisteredOnPeppol()).isTrue();
        verify(companyRepository).save(company);

        company.setRegisteredOnPeppol(false);
        assertThat(service.registerCompany(company).peppolActive()).isTrue();
        assertThat(Base64.getDecoder().decode(registrationBody.path("signedContract").asText())).isEqualTo(CONTRACT);
    }

    @Test
    void keepsTheCompanyInactiveAndContractAvailableWhenProxyRegistrationFails() {
        storage.storeContract(company.getPeppolId(), 42L, CONTRACT);
        responseStatus = 500;

        assertThat(service.registerCompany(company).peppolActive()).isFalse();

        assertThat(company.isRegisteredOnPeppol()).isFalse();
        assertThat(storage.getContract(company.getPeppolId(), 42L)).isEqualTo(CONTRACT);
    }

    @Test
    void doesNotCallTheProxyWithoutAStoredContract() {
        assertThatThrownBy(() -> service.registerCompany(company)).hasMessage("Failed to read signed contract");
        assertThat(registrationBody).isNull();
        assertThat(company.isRegisteredOnPeppol()).isFalse();
    }
}

package org.letspeppol.app.service;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.dto.CompanyDto;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.repository.CompanyRepository;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Executable proof for the App profile cache-miss -> KYC network branch. */
class CompanyNetworkFlowTest {

    @Test
    void cacheMissForwardsUserTokenToKycAndStoresCompany() {
        AtomicReference<String> authorization = new AtomicReference<>();
        DisposableServer server = HttpServer.create()
                .port(0)
                .handle((request, response) -> {
                    authorization.set(request.requestHeaders().get("Authorization"));
                    response.status(200).header("Content-Type", "application/json");
                    return response.sendString(Mono.just("""
                            {"peppolId":"0208:0123456789","identifier":"0123456789",
                             "vatNumber":"BE0123456789","companyName":"Network Company",
                             "street":"Main 1","city":"Brussels","postalCode":"1000",
                             "directorName":"Director","directorEmail":"director@example.test"}
                            """));
                })
                .bindNow();

        try {
            CompanyRepository repository = mock(CompanyRepository.class);
            when(repository.findByPeppolId("0208:0123456789")).thenReturn(Optional.empty());
            doAnswer(invocation -> invocation.getArgument(0, Company.class))
                    .when(repository).save(any(Company.class));
            CompanyService service = new CompanyService(
                    repository,
                    WebClient.create("http://localhost:" + server.port()),
                    mock(Counter.class));

            CompanyDto company = service.get("0208:0123456789", "user-access-token", true);

            assertThat(company.peppolId()).isEqualTo("0208:0123456789");
            assertThat(company.displayName()).isEqualTo("Network Company");
            assertThat(authorization.get()).isEqualTo("Bearer user-access-token");
        } finally {
            server.disposeNow();
        }
    }
}

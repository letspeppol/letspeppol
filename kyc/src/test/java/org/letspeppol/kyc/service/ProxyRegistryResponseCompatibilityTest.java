package org.letspeppol.kyc.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyRegistryResponseCompatibilityTest {

    @Test
    void acceptsTheAccessPointFieldWithoutStoringItInKyc() {
        WebClient client = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("""
                                {"peppolId":"0208:0123456789","peppolActive":true,"accessPoint":"RECOMMAND"}
                                """)
                        .build())).build();

        assertThat(new ProxyService(client).isCompanyPeppolActive("0208:0123456789")).isTrue();
    }
}

package org.letspeppol.app.service;

import org.letspeppol.app.dto.PeppolRegistrationDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PeppolRegistrationService {

    private final WebClient proxyWebClient;

    public PeppolRegistrationService(@Qualifier("serviceProxyWebClient") WebClient proxyWebClient) {
        this.proxyWebClient = proxyWebClient;
    }

    public PeppolRegistrationDto get(String peppolId) {
        try {
            return proxyWebClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/sapi/registry").queryParam("peppolId", peppolId).build())
                    .retrieve()
                    .bodyToMono(PeppolRegistrationDto.class)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty proxy registry response"));
        } catch (WebClientResponseException.NotFound ex) {
            return new PeppolRegistrationDto(peppolId, false, "NONE");
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Access Point unavailable", ex);
        }
    }
}

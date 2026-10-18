package org.letspeppol.app.service;

import org.letspeppol.app.dto.PeppolRegistrationDto;
import org.letspeppol.app.dto.PeppolSettings;
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

    public PeppolSettings getSettings(String peppolId) {
        try {
            return proxyWebClient.get()
                    .uri(builder -> builder.path("/sapi/registry/settings").queryParam("peppolId", peppolId).build())
                    .retrieve().bodyToMono(PeppolSettings.class).blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty proxy settings response"));
        } catch (WebClientResponseException ex) {
            throw settingsFailure(ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Access Point unavailable", ex);
        }
    }

    public PeppolSettings updateSettings(String peppolId, PeppolSettings settings) {
        try {
            return proxyWebClient.put()
                    .uri(builder -> builder.path("/sapi/registry/settings").queryParam("peppolId", peppolId).build())
                    .bodyValue(settings).retrieve().bodyToMono(PeppolSettings.class).blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty proxy settings response"));
        } catch (WebClientResponseException ex) {
            throw settingsFailure(ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Access Point unavailable", ex);
        }
    }

    private ResponseStatusException settingsFailure(WebClientResponseException ex) {
        if (ex.getStatusCode().value() == 409) {
            return new ResponseStatusException(HttpStatus.CONFLICT,
                    "Receiving is already registered with another Peppol provider. Unregister there first.", ex);
        }
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not load or update Peppol settings", ex);
    }
}

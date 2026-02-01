package org.letspeppol.kyc.service;

import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.RegistrationRequest;
import org.letspeppol.kyc.dto.RegistrationResponse;
import org.letspeppol.kyc.dto.RegistryDto;
import org.letspeppol.kyc.dto.ServiceRequest;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ProxyService {

    @Qualifier("ProxyWebClient")
    @Autowired
    private WebClient webClient;

    public boolean isCompanyPeppolActive(String token) {
        RegistryDto registryDto = webClient.get()
                .uri("/sapi/registry")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(RegistryDto.class)
                .blockOptional()
                .orElseThrow( () -> new KycException(KycErrorCodes.PROXY_FAILED));

        return registryDto.peppolActive();
    }

    public RegistrationResponse registerCompany(String token, String companyName) {
        try {
            RegistryDto registryDto = webClient.post()
                    .uri("/sapi/registry")
                    .body(Mono.just(new RegistrationRequest(companyName, "NL", "BE")), RegistrationRequest.class)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(RegistryDto.class)
                    .blockOptional()
                    .orElseThrow( () -> new KycException(KycErrorCodes.PROXY_REGISTRATION_FAILED));

            return new RegistrationResponse(registryDto.peppolActive(), null, null);
        } catch (WebClientResponseException e) { // HTTP error (non-2xx)
            int status = e.getRawStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("Registering company to proxy could not succeed {}: {}", status, body, e);
            return switch (status) {
                case 409 -> new RegistrationResponse(false, KycErrorCodes.PROXY_REGISTRATION_CONFLICT, body);
                case 503 -> new RegistrationResponse(false, KycErrorCodes.PROXY_REGISTRATION_UNAVAILABLE, body);
                case 500 -> new RegistrationResponse(false, KycErrorCodes.PROXY_REGISTRATION_INTERNAL_ERROR, body);
                default  -> new RegistrationResponse(false, KycErrorCodes.PROXY_FAILED, "Registering company to proxy failed with " + status + " " + body);
            };
        } catch (Exception ex) {
            log.error("Registering company to proxy failed", ex);
            return new RegistrationResponse(false, KycErrorCodes.PROXY_FAILED, "Registering company to proxy failed");
        }
    }

    public boolean unregisterCompany(String token) {
        try {
            RegistryDto registryDto = webClient.put()
                    .uri("/sapi/registry/unregister")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(RegistryDto.class)
                    .blockOptional()
                    .orElseThrow( () -> new KycException(KycErrorCodes.PROXY_UNREGISTRATION_FAILED));

            return registryDto.peppolActive();
        } catch (Exception ex) {
            log.error("Unregistering company to proxy failed", ex);
            return isCompanyPeppolActive(token);
        }
    }

    public void allowService(String token, ServiceRequest request) {
        try {
            webClient.put()
                    .uri("/sapi/registry/allow")
                    .body(Mono.just(request), ServiceRequest.class)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

        } catch (Exception ex) {
            log.error("Allowing of service on proxy failed", ex);
            throw new KycException(KycErrorCodes.PROXY_ALLOW_SERVICE_FAILED);
        }
    }

    public void rejectService(String token, ServiceRequest request) {
        try {
            webClient.put()
                    .uri("/sapi/registry/reject")
                    .body(Mono.just(request), ServiceRequest.class)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

        } catch (Exception ex) {
            log.error("Rejecting of service on proxy failed", ex);
            throw new KycException(KycErrorCodes.PROXY_REJECT_SERVICE_FAILED);
        }
    }
}

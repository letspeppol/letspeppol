package org.letspeppol.kyc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.RegistrationRequest;
import org.letspeppol.kyc.dto.RegistrationResponse;
import org.letspeppol.kyc.dto.RegistryDto;
import org.letspeppol.kyc.dto.ServiceRequest;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyService {

    @Qualifier("ProxyWebClient")
    private final WebClient webClient;
    private final JwtClaimExtractor jwtClaimExtractor;

    /** peppolId of the acting user (already validated/gated by KYC), asserted to the service-only registry endpoints. */
    private String actingPeppolId() {
        return jwtClaimExtractor.extract().peppolId();
    }

    public boolean isCompanyPeppolActive() {
        String peppolId = actingPeppolId();
        RegistryDto registryDto = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/sapi/registry").queryParam("peppolId", peppolId).build())
                .retrieve()
                .bodyToMono(RegistryDto.class)
                .blockOptional()
                .orElseThrow(() -> new KycException(KycErrorCodes.PROXY_FAILED));

        return registryDto.peppolActive();
    }

    public RegistrationResponse registerCompany(String companyName) {
        String peppolId = actingPeppolId();
        try {
            RegistryDto registryDto = webClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/sapi/registry").queryParam("peppolId", peppolId).build())
                    .body(Mono.just(new RegistrationRequest(companyName, "NL", "BE")), RegistrationRequest.class)
                    .retrieve()
                    .bodyToMono(RegistryDto.class)
                    .blockOptional()
                    .orElseThrow(() -> new KycException(KycErrorCodes.PROXY_REGISTRATION_FAILED));

            return new RegistrationResponse(registryDto.peppolActive(), null, null);
        } catch (WebClientResponseException e) {
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

    public boolean unregisterCompany() {
        String peppolId = actingPeppolId();
        try {
            RegistryDto registryDto = webClient.put()
                    .uri(uriBuilder -> uriBuilder.path("/sapi/registry/unregister").queryParam("peppolId", peppolId).build())
                    .retrieve()
                    .bodyToMono(RegistryDto.class)
                    .blockOptional()
                    .orElseThrow(() -> new KycException(KycErrorCodes.PROXY_UNREGISTRATION_FAILED));

            return registryDto.peppolActive();
        } catch (Exception ex) {
            log.error("Unregistering company to proxy failed", ex);
            return isCompanyPeppolActive();
        }
    }

    public void allowService(ServiceRequest request) {
        String peppolId = actingPeppolId();
        try {
            webClient.put()
                    .uri(uriBuilder -> uriBuilder.path("/sapi/registry/allow").queryParam("peppolId", peppolId).build())
                    .body(Mono.just(request), ServiceRequest.class)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

        } catch (Exception ex) {
            log.error("Allowing of service on proxy failed", ex);
            throw new KycException(KycErrorCodes.PROXY_ALLOW_SERVICE_FAILED);
        }
    }

    public void rejectService(ServiceRequest request) {
        String peppolId = actingPeppolId();
        try {
            webClient.put()
                    .uri(uriBuilder -> uriBuilder.path("/sapi/registry/reject").queryParam("peppolId", peppolId).build())
                    .body(Mono.just(request), ServiceRequest.class)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

        } catch (Exception ex) {
            log.error("Rejecting of service on proxy failed", ex);
            throw new KycException(KycErrorCodes.PROXY_REJECT_SERVICE_FAILED);
        }
    }
}

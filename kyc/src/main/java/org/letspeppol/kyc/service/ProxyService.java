package org.letspeppol.kyc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.RegistrationRequest;
import org.letspeppol.kyc.dto.RegistrationResponse;
import org.letspeppol.kyc.dto.RegistryDto;
import org.letspeppol.kyc.dto.ServiceRequest;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
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

    // The company acted on is passed in explicitly rather than read from the security context: KYC
    // does its own ADMIN / contract gating, and several flows (a director signing an invitation)
    // legitimately reach the proxy while unauthenticated.

    public boolean isCompanyPeppolActive(String peppolId) {
        RegistryDto registryDto = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/sapi/registry").queryParam("peppolId", peppolId).build())
                .retrieve()
                .bodyToMono(RegistryDto.class)
                .blockOptional()
                .orElseThrow(() -> new KycException(KycErrorCodes.PROXY_FAILED));

        return registryDto.peppolActive();
    }

    public RegistrationResponse registerCompany(String peppolId, String companyName) {
        return registerCompany(peppolId, companyName, null, null, null, null);
    }

    public RegistrationResponse registerCompany(String peppolId, String companyName, String address,
                                                String postalCode, String city, String vatNumber) {
        try {
            RegistryDto registryDto = webClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/sapi/registry").queryParam("peppolId", peppolId).build())
                    .body(Mono.just(new RegistrationRequest(
                            companyName,
                            "EN", //TODO : not default NL
                            "BE",
                            address,
                            postalCode,
                            city,
                            vatNumber
                    )), RegistrationRequest.class)
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

    public boolean unregisterCompany(String peppolId) {
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
            return isCompanyPeppolActive(peppolId);
        }
    }

    public void allowService(String peppolId, ServiceRequest request) {
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

    public void rejectService(String peppolId, ServiceRequest request) {
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

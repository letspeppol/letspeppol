package org.letspeppol.kyc.service;

import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.RegistrationRequest;
import org.letspeppol.kyc.dto.RegistryDto;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ProxyService {

    @Value("${proxy.enabled}")
    private boolean proxyEnabled;

    @Qualifier("ProxyWebClient")
    @Autowired
    private WebClient webClient;

    public boolean isCompanyPeppolActive(String token) {
        RegistryDto registryDto = this.webClient.get()
                .uri("/sapi/registry")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(RegistryDto.class)
                .blockOptional()
                .orElseThrow( () -> new KycException(KycErrorCodes.PROXY_FAILED));

        return registryDto.peppolActive();
    }

    public boolean registerCompany(String token, String companyName) {
        if (!proxyEnabled) {
            return false;
        }
        try {
            RegistryDto registryDto = this.webClient.post()
                    .uri("/sapi/registry")
                    .body(Mono.just(new RegistrationRequest(companyName, "NL", "BE")), RegistrationRequest.class)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(RegistryDto.class)
                    .blockOptional()
                    .orElseThrow( () -> new KycException(KycErrorCodes.PROXY_REGISTRATION_FAILED));

            return registryDto.peppolActive();
        } catch (Exception ex) {
            log.error("Registering company to proxy failed", ex);
            return false;
        }
    }

    public boolean unregisterCompany(String token) {
        if (!proxyEnabled) { //TODO : do we still need this ?
            return false;
        }
        try {
            RegistryDto registryDto = this.webClient.put()
                    .uri("/sapi/registry/suspend")
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

}

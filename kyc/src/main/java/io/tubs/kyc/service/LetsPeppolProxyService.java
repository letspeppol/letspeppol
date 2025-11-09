package io.tubs.kyc.service;

import io.tubs.kyc.dto.ProxyRegistrationRequest;
import io.tubs.kyc.exception.KycErrorCodes;
import io.tubs.kyc.exception.KycException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class LetsPeppolProxyService {

    @Value("${proxy.enabled}")
    private boolean proxyEnabled;

    @Qualifier("ProxyWebClient")
    @Autowired
    private  WebClient webClient;

    public void registerCompany(String token, String companyName) {
        if (!proxyEnabled) {
            return;
        }
        try {
            ResponseEntity<String> response = this.webClient.post()
                    .uri("/reg")
                    .body(Mono.just(new ProxyRegistrationRequest(companyName)), ProxyRegistrationRequest.class)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toEntity(String.class)
                    .block();
        } catch (Exception ex) {
            log.error("Registering company to proxy failed", ex);
            throw new KycException(KycErrorCodes.PROXY_REGISTRATION_FAILED);
        }
    }

    public void unregisterCompany(String token) {
        if (!proxyEnabled) {
            return;
        }
        try {
            ResponseEntity<String> response = this.webClient.post()
                    .uri("/unreg")
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .toEntity(String.class)
                    .block();
        } catch (Exception ex) {
            log.error("Unregistering company to proxy failed", ex);
            throw new KycException(KycErrorCodes.PROXY_UNREGISTRATION_FAILED);
        }
    }

}

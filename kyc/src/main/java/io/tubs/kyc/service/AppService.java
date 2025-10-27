package io.tubs.kyc.service;

import io.tubs.kyc.dto.AppRegistrationRequest;
import io.tubs.kyc.dto.IdentityVerificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class AppService {

    @Value("${app.enabled}")
    private boolean appEnabled;
    private WebClient webClient;
    private JwtService jwtService;

    private String activeToken;

    public AppService(@Qualifier("ProxyWebClient") WebClient webClient, JwtService jwtService) {
        this.webClient = webClient;
        this.jwtService = jwtService;
        this.activeToken = jwtService.generateInternalToken();
    }

    public void register(IdentityVerificationRequest identity) {
        if (!appEnabled) {
            return;
        }
        AppRegistrationRequest request = new AppRegistrationRequest(
                identity.director().getCompany().getCompanyNumber(),
                identity.director().getCompany().getName(),
                identity.director().getCompany().getStreet(),
                identity.director().getCompany().getHouseNumber(),
                identity.director().getCompany().getCity(),
                identity.director().getCompany().getPostalCode(),
                identity.director().getName(),
                identity.email()
        );
        try {
            ResponseEntity<Void> response = this.webClient.post()
                    .uri("/api/internal/company/register")
                    .header("Authorization", "Bearer " + activeToken)
                    .body(Mono.just(request), AppRegistrationRequest.class)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception ex) {
            log.error("Registering identity to App failed", ex);
            // Retry again
        }
    }

    @Scheduled(cron = "0 0 */12 * * *")
    public void refreshToken() {
        this.activeToken = jwtService.generateInternalToken();
        log.info("Service token refreshed");
    }
}

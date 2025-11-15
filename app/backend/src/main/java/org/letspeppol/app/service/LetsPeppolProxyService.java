package org.letspeppol.app.service;

import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.dto.proxy.MaxProcessedDto;
import org.letspeppol.app.dto.proxy.TotalProcessedDto;
import org.letspeppol.app.exception.AppErrorCodes;
import org.letspeppol.app.exception.AppException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class LetsPeppolProxyService {

    private final boolean proxyEnabled;
    private final WebClient webClient;
    private final JwtService jwtService;
    private String activeToken;

    public LetsPeppolProxyService(JwtService jwtService, @Qualifier("ProxyWebClient") WebClient webClient, @Value("${proxy.enabled}") boolean proxyEnabled) {
        this.jwtService = jwtService;
        this.webClient = webClient;
        this.proxyEnabled = proxyEnabled;
        this.activeToken = jwtService.generateInternalToken();
    }

    @Scheduled(cron = "0 0 */12 * * *")
    public void refreshToken() {
        this.activeToken = jwtService.generateInternalToken();
        log.info("Service token refreshed");
    }

    public TotalProcessedDto totalsProcessed() {
        if (!proxyEnabled) {
            return null;
        }
        try {
            return this.webClient.get()
                    .uri("/v2/stats/totals")
                    .header("Authorization", "Bearer " + activeToken)
                    .retrieve()
                    .bodyToMono(TotalProcessedDto.class)
                    .block();
        } catch (Exception ex) {
            log.error("Call to proxy /v2/stats/totals failed", ex);
            throw new AppException(AppErrorCodes.PROXY_REST_ERROR);
        }
    }

    public MaxProcessedDto maxProcessed() {
        if (!proxyEnabled) {
            return null;
        }
        try {
            return this.webClient.get()
                    .uri("/v2/stats/max")
                    .header("Authorization", "Bearer " + activeToken)
                    .retrieve()
                    .bodyToMono(MaxProcessedDto.class)
                    .block();
        } catch (Exception ex) {
            log.error("Call to proxy /v2/stats/max failed", ex);
            throw new AppException(AppErrorCodes.PROXY_REST_ERROR);
        }
    }
}

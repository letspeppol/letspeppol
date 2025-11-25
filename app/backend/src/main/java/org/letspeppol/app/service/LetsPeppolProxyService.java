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

    /* *
        SELECT
          SUM(CASE WHEN direction = 'incoming' AND paid IS NULL THEN amount ELSE 0 END) AS totalPayableOpen,
          SUM(CASE WHEN direction = 'incoming' AND paid IS NULL AND duedate < NOW() THEN amount ELSE 0 END) AS totalPayableOverdue,
          SUM(CASE WHEN direction = 'incoming' AND EXTRACT(YEAR FROM createdat) = EXTRACT(YEAR FROM NOW()) THEN amount ELSE 0 END) AS totalPayableThisYear,
          SUM(CASE WHEN direction = 'outgoing' AND paid IS NULL THEN amount ELSE 0 END) AS totalReceivableOpen,
          SUM(CASE WHEN direction = 'outgoing' AND paid IS NULL AND duedate < NOW() THEN amount ELSE 0 END) AS totalReceivableOverdue,
          SUM(CASE WHEN direction = 'outgoing' AND EXTRACT(YEAR FROM createdat) = EXTRACT(YEAR FROM NOW()) THEN amount ELSE 0 END) AS totalReceivableThisYear
        FROM FrontDocs
        WHERE userId = $1
    * */
    // getTotals

    /* *
        SELECT
          (SELECT COUNT(*) FROM frontdocs) AS totalProcessed,
          (SELECT COUNT(*)
            FROM frontdocs
            WHERE createdAt >= date_trunc('day', CURRENT_DATE)
              AND createdAt < date_trunc('day', CURRENT_DATE + INTERVAL '1 day')
          ) AS totalProcessedToday;`

    * */
    public TotalProcessedDto totalsProcessed() {
//        if (!proxyEnabled) {
            return null;
//        }
//        try {
//            return this.webClient.get()
//                    .uri("/v2/stats/totals")
//                    .header("Authorization", "Bearer " + activeToken)
//                    .retrieve()
//                    .bodyToMono(TotalProcessedDto.class)
//                    .block();
//        } catch (Exception ex) {
//            log.error("Call to proxy /v2/stats/totals failed", ex);
//            throw new AppException(AppErrorCodes.PROXY_REST_ERROR);
//        }
    }

    /* *
        SELECT MAX(day_count) AS maxDailyTotal
        FROM (
             SELECT date_trunc('day', createdAt) AS day, COUNT(*) AS day_count
             FROM frontdocs
             WHERE createdAt >= date_trunc('day', CURRENT_DATE - INTERVAL '7 days')
               AND createdAt < date_trunc('day', CURRENT_DATE)
             GROUP BY 1
        ) AS daily_counts;
    * */
    public MaxProcessedDto maxProcessed() {
//        if (!proxyEnabled) {
            return null;
//        }
//        try {
//            return this.webClient.get()
//                    .uri("/v2/stats/max")
//                    .header("Authorization", "Bearer " + activeToken)
//                    .retrieve()
//                    .bodyToMono(MaxProcessedDto.class)
//                    .block();
//        } catch (Exception ex) {
//            log.error("Call to proxy /v2/stats/max failed", ex);
//            throw new AppException(AppErrorCodes.PROXY_REST_ERROR);
//        }
    }
}

package org.letspeppol.app.service;

import org.letspeppol.app.dto.DonationStatsDto;
import org.letspeppol.app.dto.OpenCollectiveAccountDto;

import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.dto.MaxProcessedDto;
import org.letspeppol.app.dto.TotalProcessedDto;
import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
@Slf4j
public class DonationService {

    @Value("${document.price}")
    private BigDecimal pricePerDocument;
    private final StatisticsService statisticsService;
    private final HttpGraphQlClient graphQlClient; // Open collective
    private DonationStatsDto donationStatsDto;
    private MaxProcessedDto maxProcessedDto;

    public DonationService(@Qualifier("OpenCollectiveWebClient") WebClient webClient, StatisticsService statisticsService) {
        this.graphQlClient = HttpGraphQlClient.builder(webClient).build();
        this.statisticsService = statisticsService;
    }

    public DonationStatsDto getDonationStats() {
        if (this.donationStatsDto == null) {
            this.updateDonationStats();
        }
        return this.donationStatsDto;
    }

    private MaxProcessedDto getMaxProcessedDto() {
        if (this.maxProcessedDto == null) {
            updateMaxProcessedDto();
        }
        return this.maxProcessedDto;
    }

    @Scheduled(cron = "0 0 */12 * * *")
    public synchronized void updateMaxProcessedDto() {
        this.maxProcessedDto = this.statisticsService.maxProcessed();
    }

    @Scheduled(fixedRateString = "PT15M")
    public synchronized void updateDonationStats() {
        TotalProcessedDto totalProcessedDto = statisticsService.totalsProcessed();
        if (totalProcessedDto == null) return;
        OpenCollectiveAccountDto accountInfo = this.queryAccount().block();

        Integer invoicesRemaining = accountInfo.getStats().getTotalAmountReceived().getValue()
                .subtract(
                        pricePerDocument.multiply(BigDecimal.valueOf(totalProcessedDto.totalProcessed()))
                )
                .divide(pricePerDocument, RoundingMode.HALF_EVEN)
                .toBigInteger().intValue();

        donationStatsDto = DonationStatsDto.builder()
                .totalContributions(accountInfo.getStats().getContributionsCount())
                .totalProcessed(totalProcessedDto.totalProcessed())
                .processedToday(totalProcessedDto.totalProcessedToday())
                .maxProcessedLastWeek(getMaxProcessedDto().maxDailyTotal())
                .invoicesRemaining(invoicesRemaining)
                .transactions(accountInfo.getTransactions().getNodes())
                .build();
    }

    private Mono<OpenCollectiveAccountDto> queryAccount() {
        String document = """
                        query account($slug: String) {
                            account(slug: $slug) {
                                name
                                slug
                                transactions(limit: 3, type: CREDIT) {
                                    totalCount
                                    nodes {
                                        type
                                        fromAccount {
                                            name
                                            slug
                                        }
                                        amount {
                                            value
                                            currency
                                        }
                                        createdAt
                                    }
                                }
                                stats {
                                    balance {
                                        value
                                        currency
                                    }
                                    totalAmountReceived {
                                        value
                                        currency
                                    }
                                    totalAmountSpent {
                                        value
                                        currency
                                    }
                                    contributionsCount
                                    contributorsCount
                                }
                            }
                        }
                        """;

        Map<String, Object> variables = Map.of(
                "slug", "letspeppol",
                "limit", 3
        );
        return graphQlClient.document(document).variables(variables).retrieve("account").toEntity(OpenCollectiveAccountDto.class);
    }
}

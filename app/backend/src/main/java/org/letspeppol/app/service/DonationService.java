package org.letspeppol.app.service;

import org.letspeppol.app.dto.DonationStatsDto;
import org.letspeppol.app.dto.OpenCollectiveAccountDto;

import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.dto.MaxProcessedDto;
import org.letspeppol.app.dto.SponsorContributionDto;
import org.letspeppol.app.dto.TotalProcessedDto;
import org.letspeppol.app.model.SponsorInvoice;
import org.letspeppol.app.repository.SponsorInvoiceRepository;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DonationService {

    private static final int TRANSACTION_LIMIT = 100;

    @Value("${document.price}")
    private BigDecimal pricePerDocument;
    private final StatisticsService statisticsService;
    private final SponsorInvoiceRepository sponsorInvoiceRepository;
    private final HttpGraphQlClient graphQlClient; // Open collective
    private DonationStatsDto donationStatsDto;
    private MaxProcessedDto maxProcessedDto;

    public DonationService(@Qualifier("OpenCollectiveWebClient") WebClient webClient,
                           StatisticsService statisticsService,
                           SponsorInvoiceRepository sponsorInvoiceRepository) {
        this.graphQlClient = HttpGraphQlClient.builder(webClient).build();
        this.statisticsService = statisticsService;
        this.sponsorInvoiceRepository = sponsorInvoiceRepository;
    }

    public DonationStatsDto getDonationStats() {
        if (this.donationStatsDto == null) {
            this.updateDonationStats();
        }
        return this.donationStatsDto;
    }

    public synchronized void clearDonationStatsCache() {
        this.donationStatsDto = null;
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
        List<OpenCollectiveAccountDto.Transaction> transactions = transactionsOf(accountInfo);
        List<SponsorContributionDto> peppolContributions = sponsorInvoiceRepository.findAllByActiveTrueOrderBySponsoredOnDesc().stream()
                .map(this::toContribution)
                .toList();
        List<SponsorContributionDto> contributions = combinedContributions(peppolContributions, transactions);

        Long invoicesRemaining = totalAmountReceived(accountInfo)
                .subtract(
                        pricePerDocument.multiply(BigDecimal.valueOf(totalProcessedDto.totalProcessed()))
                )
                .divide(pricePerDocument, RoundingMode.HALF_EVEN)
                .toBigInteger().longValue();

        donationStatsDto = DonationStatsDto.builder()
                .totalContributions(contributionsCount(accountInfo) + peppolContributions.size())
                .totalProcessed(totalProcessedDto.totalProcessed())
                .processedToday(totalProcessedDto.totalProcessedToday())
                .maxProcessedLastWeek(getMaxProcessedDto().maxDailyTotal())
                .invoicesRemaining(invoicesRemaining)
                .activeCompanies(statisticsService.activeCompanies())
                .transactions(transactions)
                .contributions(contributions)
                .build();
    }

    private SponsorContributionDto toContribution(SponsorInvoice sponsorInvoice) {
        return new SponsorContributionDto(
                sponsorInvoice.getName(),
                sponsorInvoice.getMessage(),
                sponsorInvoice.getAmount(),
                sponsorInvoice.getCurrency().getCurrencyCode(),
                sponsorInvoice.getSponsoredOn()
        );
    }

    private List<SponsorContributionDto> combinedContributions(
            List<SponsorContributionDto> peppolContributions,
            List<OpenCollectiveAccountDto.Transaction> transactions
    ) {
        List<SponsorContributionDto> contributions = new ArrayList<>(peppolContributions);
        if (transactions != null) {
            contributions.addAll(transactions.stream()
                    .filter(transaction -> transaction.getAmount() != null && transaction.getAmount().getValue() != null)
                    .map(transaction -> new SponsorContributionDto(
                            openCollectiveName(transaction),
                            "OpenCollective contribution",
                            transaction.getAmount().getValue(),
                            transaction.getAmount().getCurrency(),
                            openCollectiveDate(transaction)
                    ))
                    .toList());
        }
        return contributions.stream()
                .sorted(Comparator.comparing(
                        SponsorContributionDto::date,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    private Mono<OpenCollectiveAccountDto> queryAccount() {
        String document = """
                        query account($slug: String, $limit: Int) {
                            account(slug: $slug) {
                                name
                                slug
                                transactions(limit: $limit, type: CREDIT) {
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
                "limit", TRANSACTION_LIMIT
        );
        return graphQlClient.document(document).variables(variables).retrieve("account").toEntity(OpenCollectiveAccountDto.class);
    }

    private String openCollectiveName(OpenCollectiveAccountDto.Transaction transaction) {
        if (transaction.getFromAccount() == null || transaction.getFromAccount().getName() == null || transaction.getFromAccount().getName().isBlank()) {
            return "OpenCollective supporter";
        }
        return transaction.getFromAccount().getName();
    }

    private Instant openCollectiveDate(OpenCollectiveAccountDto.Transaction transaction) {
        if (transaction.getCreatedAt() == null || transaction.getCreatedAt().isBlank()) {
            return null;
        }
        return Instant.parse(transaction.getCreatedAt());
    }

    private List<OpenCollectiveAccountDto.Transaction> transactionsOf(OpenCollectiveAccountDto accountInfo) {
        if (accountInfo == null || accountInfo.getTransactions() == null || accountInfo.getTransactions().getNodes() == null) {
            return List.of();
        }
        return accountInfo.getTransactions().getNodes();
    }

    private BigDecimal totalAmountReceived(OpenCollectiveAccountDto accountInfo) {
        if (accountInfo == null
                || accountInfo.getStats() == null
                || accountInfo.getStats().getTotalAmountReceived() == null
                || accountInfo.getStats().getTotalAmountReceived().getValue() == null) {
            return BigDecimal.ZERO;
        }
        return accountInfo.getStats().getTotalAmountReceived().getValue();
    }

    private long contributionsCount(OpenCollectiveAccountDto accountInfo) {
        if (accountInfo == null || accountInfo.getStats() == null || accountInfo.getStats().getContributionsCount() == null) {
            return 0;
        }
        return accountInfo.getStats().getContributionsCount();
    }
}

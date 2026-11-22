package org.letspeppol.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.letspeppol.app.config.DonationConfig;
import org.letspeppol.app.dto.MaxProcessedDto;
import org.letspeppol.app.dto.SponsorContributionDto;
import org.letspeppol.app.dto.TotalProcessedDto;
import org.letspeppol.app.model.SponsorInvoice;
import org.letspeppol.app.repository.SponsorInvoiceRepository;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.graphql.client.HttpGraphQlClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DonationServiceTest {

    private final StatisticsService statistics = mock(StatisticsService.class);
    private final SponsorInvoiceRepository sponsorInvoices = mock(SponsorInvoiceRepository.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DonationConfig.class, DonationService.class)
            .withBean(StatisticsService.class, () -> statistics)
            .withBean(SponsorInvoiceRepository.class, () -> sponsorInvoices)
            .withPropertyValues("document.price=0.01");

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " \t ")
    void keepsLocalDonationStatsWithoutAnOpenCollectiveKey(String apiKey) {
        when(statistics.totalsProcessed()).thenReturn(new TotalProcessedDto(12L, 3L));
        when(statistics.maxProcessed()).thenReturn(new MaxProcessedDto(7L));
        when(statistics.activeCompanies()).thenReturn(2L);
        var sponsor = new SponsorInvoice(null, Instant.parse("2026-01-01T00:00:00Z"), BigDecimal.TEN,
                Currency.getInstance("EUR"), "Local sponsor", "Thanks", "invoice-1");
        when(sponsorInvoices.findAllByActiveTrueOrderBySponsoredOnDesc()).thenReturn(List.of(sponsor));

        var runner = apiKey == null ? contextRunner : contextRunner.withInitializer(context ->
                context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("testApiKey", Map.of("open-collective.api-key", apiKey))));
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(HttpGraphQlClient.class);
            var stats = context.getBean(DonationService.class).getDonationStats();
            assertThat(stats.getTotalProcessed()).isEqualTo(12L);
            assertThat(stats.getActiveCompanies()).isEqualTo(2L);
            assertThat(stats.getTransactions()).isEmpty();
            assertThat(stats.getTotalContributions()).isEqualTo(1L);
            assertThat(stats.getContributions()).extracting(SponsorContributionDto::name).containsExactly("Local sponsor");
        });
    }

    @Test
    void fetchesOpenCollectiveStatsWithTheConfiguredKey() {
        when(statistics.totalsProcessed()).thenReturn(new TotalProcessedDto(0L, 0L));
        when(statistics.maxProcessed()).thenReturn(new MaxProcessedDto(0L));
        var apiKey = new AtomicReference<String>();
        var server = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
            apiKey.set(request.requestHeaders().get("Api-Key"));
            return response.header("Content-Type", "application/json").sendString(Mono.just("""
                    {"data":{"account":{"stats":{"contributionsCount":5,"totalAmountReceived":{"value":10}}}}}
                    """));
        }).bindNow();
        try {
            contextRunner.withPropertyValues("open-collective.api-key=test-key",
                            "open-collective.url=http://127.0.0.1:" + server.port() + "/graphql")
                    .run(context -> {
                        assertThat(context).hasSingleBean(HttpGraphQlClient.class);
                        var stats = context.getBean(DonationService.class).getDonationStats();
                        assertThat(stats.getTotalContributions()).isEqualTo(5L);
                        assertThat(stats.getInvoicesRemaining()).isEqualTo(1000L);
                        assertThat(apiKey.get()).isEqualTo("test-key");
                    });
        } finally {
            server.disposeNow();
        }
    }
}

package org.letspeppol.app.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter companyCreateCounter(MeterRegistry registry) {
        return Counter.builder("company_create_total")
                .description("Total # created companies")
                .tag("service", "app")
                .register(registry);
    }

    @Bean
    public Counter partnerCreateCounter(MeterRegistry registry) {
        return Counter.builder("partner_create_total")
                .description("Total # created partners")
                .tag("service", "app")
                .register(registry);
    }

    @Bean
    public Counter productCreateCounter(MeterRegistry registry) {
        return Counter.builder("product_create_total")
                .description("Total # created products")
                .tag("service", "app")
                .register(registry);
    }

    @Bean
    public Counter documentBackupCounter(MeterRegistry registry) {
        return Counter.builder("document_backup_total")
                .description("Total # documents backed up")
                .tag("service", "app")
                .register(registry);
    }

    @Bean
    public Counter documentCreateCounter(MeterRegistry registry) {
        return Counter.builder("document_create_total")
                .description("Total # documents created")
                .tag("service", "app")
                .register(registry);
    }

    @Bean
    public Counter documentSendCounter(MeterRegistry registry) {
        return Counter.builder("document_send_app_total")
                .description("Total # documents sent via app")
                .tag("service", "app")
                .register(registry);
    }

    @Bean
    public Counter documentPaidCounter(MeterRegistry registry) {
        return Counter.builder("document_paid_total")
                .description("Total # documents paid")
                .tag("service", "app")
                .register(registry);
    }
}

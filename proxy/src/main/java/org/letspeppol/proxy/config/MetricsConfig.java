package org.letspeppol.proxy.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter registerCounter(MeterRegistry registry) {
        return Counter.builder("register_total")
                .description("Total # registrations")
                .tag("service", "proxy")
                .register(registry);
    }

    @Bean
    public Counter unregisterCounter(MeterRegistry registry) {
        return Counter.builder("unregister_total")
                .description("Total # unregistrations")
                .tag("service", "proxy")
                .register(registry);
    }

    @Bean
    public Counter documentSendCounter(MeterRegistry registry) {
        return Counter.builder("document_send_total")
                .description("Total # sent documents")
                .tag("service", "proxy")
                .register(registry);
    }

    @Bean
    public Counter documentReceivedCounter(MeterRegistry registry) {
        return Counter.builder("document_received_total")
                .description("Total # received documents")
                .tag("service", "proxy")
                .register(registry);
    }

    @Bean
    public Counter documentRescheduleCounter(MeterRegistry registry) {
        return Counter.builder("document_reschedule_total")
                .description("Total # reschedules")
                .tag("service", "proxy")
                .register(registry);
    }

}

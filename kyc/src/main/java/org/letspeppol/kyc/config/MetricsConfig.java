package org.letspeppol.kyc.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter companyRegistrationCounterSuccess(MeterRegistry registry) {
        return Counter.builder("company_registration_total")
                .description("Total # completed company registrations")
                .tag("service", "kyc")
                .register(registry);
    }

    @Bean
    public Counter companyRegistrationCounterFailure(MeterRegistry registry) {
        return Counter.builder("company_registration_total")
                .description("Total # failed company registrations")
                .tag("service", "kyc")
                .register(registry);
    }

    @Bean
    public Counter companyUnregistrationCounter(MeterRegistry registry) {
        return Counter.builder("company_unregistration_total")
                .description("Total # unregistrations")
                .tag("service", "kyc")
                .register(registry);
    }

    @Bean
    public Counter activationRequestedCounter(MeterRegistry registry) {
        return Counter.builder("activation_requested_total")
                .description("Total # requested activations")
                .tag("service", "kyc")
                .register(registry);
    }

    @Bean
    public Counter tokenVerificationCounter(MeterRegistry registry) {
        return Counter.builder("token_verification_total")
                .description("Total # token verifications")
                .tag("service", "kyc")
                .register(registry);
    }

    @Bean
    public Counter kboLookupCounter(MeterRegistry registry) {
        return Counter.builder("kbo_lookup_total")
                .description("Total # KBO loookups")
                .tag("service", "kyc")
                .register(registry);
    }

    @Bean
    public Counter prepareSigningCounter(MeterRegistry registry) {
        return Counter.builder("signing_prepare_total")
                .description("Total # prepare signing")
                .tag("service", "kyc")
                .register(registry);
    }

    @Bean
    public Counter finalizeSigningCounter(MeterRegistry registry) {
        return Counter.builder("signing_finalize_total")
                .description("Total # finalize signing")
                .tag("service", "kyc")
                .register(registry);
    }

    @Bean
    public Counter authenticationCounterSuccess(MeterRegistry registry) {
        return Counter.builder("auth_total")
                .description("Total # successful authentications")
                .tag("service", "kyc")
                .register(registry);
    }

    @Bean
    public Counter authenticationCounterFailure(MeterRegistry registry) {
        return Counter.builder("auth_total")
                .description("Total # failed authentications")
                .tag("service", "kyc")
                .register(registry);
    }

}

package org.letspeppol.proxy.config;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.service.AccessPointServiceRegistry;
import org.letspeppol.proxy.service.ScradaService;
import org.letspeppol.proxy.service.UblDocumentReceiverService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ScradaConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ScradaConfig.class, AccessPointServiceRegistry.class)
            .withBean(UblDocumentReceiverService.class, () -> mock(UblDocumentReceiverService.class))
            .withBean(Counter.class, () -> mock(Counter.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " \t ")
    void doesNotRegisterScradaWithoutAnApiKey(String apiKey) {
        var runner = apiKey == null ? contextRunner : contextRunner.withInitializer(context ->
                context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("testApiKey", Map.of("SCRADA_API_KEY", apiKey))));
        runner.run(context -> {
            assertThat(context).doesNotHaveBean("scradaWebClient").doesNotHaveBean(ScradaService.class);
            assertThat(context.getBean(AccessPointServiceRegistry.class).get(AccessPoint.SCRADA)).isNull();
        });
    }

    @Test
    void registersScradaWithConfiguredCredentials() {
        contextRunner.withPropertyValues("SCRADA_API_KEY=test-key", "SCRADA_API_PWD=test-password",
                        "SCRADA_COMPANY_ID=test-company")
                .run(context -> {
                    assertThat(context).hasBean("scradaWebClient").hasSingleBean(ScradaService.class);
                    assertThat(context.getBean(AccessPointServiceRegistry.class).get(AccessPoint.SCRADA))
                            .isSameAs(context.getBean(ScradaService.class));
                });
    }

    @Test
    void reportsMissingPasswordWhenAnApiKeyIsProvided() {
        contextRunner.withPropertyValues("SCRADA_API_KEY=test-key", "SCRADA_COMPANY_ID=test-company")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("SCRADA_API_PWD must be set when SCRADA_API_KEY is provided"));
    }
}

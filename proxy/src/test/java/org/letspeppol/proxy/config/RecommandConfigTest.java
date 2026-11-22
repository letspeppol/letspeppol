package org.letspeppol.proxy.config;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.repository.RegistryRepository;
import org.letspeppol.proxy.service.AccessPointServiceRegistry;
import org.letspeppol.proxy.service.RecommandService;
import org.letspeppol.proxy.service.UblDocumentReceiverService;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RecommandConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(RecommandConfig.class, AccessPointServiceRegistry.class)
            .withBean(UblDocumentReceiverService.class, () -> mock(UblDocumentReceiverService.class))
            .withBean(RegistryRepository.class, () -> mock(RegistryRepository.class))
            .withBean(Counter.class, () -> mock(Counter.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void doesNotRegisterRecommandWithBlankCredentials() {
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("testCredentials", Map.of(
                                "RECOMMAND_API_KEY", "", "RECOMMAND_API_SECRET", " \t "))))
                .run(context -> {
                    assertThat(context).doesNotHaveBean("recommandWebClient").doesNotHaveBean(RecommandService.class);
                    assertThat(context.getBean(AccessPointServiceRegistry.class).get(AccessPoint.RECOMMAND)).isNull();
                });
    }

    @Test
    void registersRecommandWithBothCredentials() {
        contextRunner.withPropertyValues("RECOMMAND_API_KEY=test-key", "RECOMMAND_API_SECRET=test-secret")
                .run(context -> {
                    assertThat(context).hasBean("recommandWebClient").hasSingleBean(RecommandService.class);
                    assertThat(context.getBean(AccessPointServiceRegistry.class).get(AccessPoint.RECOMMAND))
                            .isSameAs(context.getBean(RecommandService.class));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECOMMAND_API_KEY", "RECOMMAND_API_SECRET"})
    void rejectsPartialCredentials(String presentCredential) {
        contextRunner.withPropertyValues("RECOMMAND_API_KEY=", "RECOMMAND_API_SECRET=")
                .withPropertyValues(presentCredential + "=test-value")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseMessage("Set both RECOMMAND_API_KEY and RECOMMAND_API_SECRET, or leave both blank"));
    }
}

package org.letspeppol.proxy.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.letspeppol.proxy.repository.RegistryRepository;
import org.letspeppol.proxy.service.AccessPointServiceRegistry;
import org.letspeppol.proxy.service.PhossService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PhossConfigTest {
    @TempDir Path directory;
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PhossConfig.class, AccessPointServiceRegistry.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(RegistryRepository.class, () -> mock(RegistryRepository.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void disabledByDefault() {
        runner.run(c -> assertThat(c).doesNotHaveBean(PhossService.class).doesNotHaveBean("phossApWebClient"));
    }

    @Test
    void enabledWithCompleteConfiguration() throws Exception {
        Path token = directory.resolve("token");
        Files.writeString(token, "server-side-secret");
        runner.withPropertyValues("phoss.enabled=true", "phoss.ap-url=http://ap:8080", "phoss.smp-url=http://smp:8080",
                        "phoss.token-file=" + token)
                .run(c -> assertThat(c).hasNotFailed().hasSingleBean(PhossService.class)
                        .hasBean("phossApWebClient").hasBean("phossSmpWebClient"));
    }

    @Test
    void enabledFailsOnEmptyTokenWithoutDisclosingIt() throws Exception {
        Path token = directory.resolve("token");
        Files.writeString(token, "\n");
        runner.withPropertyValues("phoss.enabled=true", "phoss.ap-url=http://ap:8080", "phoss.smp-url=http://smp:8080",
                        "phoss.token-file=" + token)
                .run(c -> assertThat(c.getStartupFailure()).hasRootCauseMessage("PHOSS token file must contain one nonempty token"));
    }

    @Test
    void rejectsCredentialsEmbeddedInSmpUrl() {
        assertThatThrownBy(() -> new PhossConfig().phossSmpWebClient("http://user:secret@smp:8080"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
    }
}

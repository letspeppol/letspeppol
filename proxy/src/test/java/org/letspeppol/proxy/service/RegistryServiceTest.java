package org.letspeppol.proxy.service;

import org.junit.jupiter.api.Test;
import org.letspeppol.proxy.dto.RegistryDto;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.Registry;
import org.letspeppol.proxy.repository.RegistryRepository;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistryServiceTest {

    @Test
    void returnsTheStoredProviderWithoutExposingProviderCredentials() {
        RegistryRepository repository = mock(RegistryRepository.class);
        when(repository.findById("0208:0123456789")).thenReturn(Optional.of(
                new Registry("0208:0123456789", AccessPoint.SCRADA, Map.of("apiKey", "secret"))));

        RegistryDto result = new RegistryService(repository, mock(AccessPointServiceRegistry.class))
                .get("0208:0123456789");

        assertThat(result).isEqualTo(new RegistryDto("0208:0123456789", true, "SCRADA"));
    }

    @Test
    void unregisteredCompanyHasNoAccessPoint() {
        RegistryRepository repository = mock(RegistryRepository.class);
        when(repository.findById("0208:0123456789")).thenReturn(Optional.of(
                new Registry("0208:0123456789", AccessPoint.NONE, null)));

        RegistryDto result = new RegistryService(repository, mock(AccessPointServiceRegistry.class))
                .get("0208:0123456789");

        assertThat(result).isEqualTo(new RegistryDto("0208:0123456789", false, "NONE"));
    }
}

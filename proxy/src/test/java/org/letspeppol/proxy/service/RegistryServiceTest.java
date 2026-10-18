package org.letspeppol.proxy.service;

import org.junit.jupiter.api.Test;
import org.letspeppol.proxy.dto.RegistryDto;
import org.letspeppol.proxy.dto.PeppolSettings;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.Registry;
import org.letspeppol.proxy.repository.RegistryRepository;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

class RegistryServiceTest {

    @Test
    void correctsOnlyTheMissingCompanyLocallyAndRetainsItsProviderVariables() {
        RegistryRepository repository = mock(RegistryRepository.class);
        AccessPointServiceRegistry providers = mock(AccessPointServiceRegistry.class);
        Registry registry = new Registry("0208:0123456789", AccessPoint.RECOMMAND,
                Map.of("companyId", "missing-company"));
        registry.setReceiveDocuments(true);
        when(repository.findById(registry.getPeppolId())).thenReturn(Optional.of(registry));

        new RegistryService(repository, providers).markNotRegistered(registry.getPeppolId(), AccessPoint.RECOMMAND);

        assertThat(registry.getAccessPoint()).isEqualTo(AccessPoint.NONE);
        assertThat(registry.getReceiveDocuments()).isFalse();
        assertThat(registry.getVariables()).containsEntry("companyId", "missing-company");
        verify(repository).save(registry);
        verifyNoInteractions(providers);
    }

    @Test
    void doesNotDisableRegistrationThatHasChangedProvider() {
        RegistryRepository repository = mock(RegistryRepository.class);
        Registry registry = new Registry("0208:0123456789", AccessPoint.SCRADA, Map.of());
        when(repository.findById(registry.getPeppolId())).thenReturn(Optional.of(registry));

        new RegistryService(repository, mock(AccessPointServiceRegistry.class))
                .markNotRegistered(registry.getPeppolId(), AccessPoint.RECOMMAND);

        assertThat(registry.getAccessPoint()).isEqualTo(AccessPoint.SCRADA);
        verify(repository, never()).save(any());
    }

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

    @Test
    void settingsReadsUpdateTheManagedReceivingFlagWithoutChangingRegistrationVariables() {
        var repository = mock(RegistryRepository.class);
        var providers = mock(AccessPointServiceRegistry.class);
        var provider = mock(AccessPointServiceInterface.class);
        var registry = new Registry("0208:0123456789", AccessPoint.RECOMMAND, Map.of("companyId", "company-1"));
        when(repository.findByIdForSettings(registry.getPeppolId())).thenReturn(Optional.of(registry));
        when(providers.get(AccessPoint.RECOMMAND)).thenReturn(provider);
        when(provider.getSettings(any())).thenAnswer(invocation -> {
            invocation.<Registry>getArgument(0).setReceiveDocuments(true);
            return new PeppolSettings(true);
        });
        var service = new RegistryService(repository, providers);

        assertThat(service.getSettings(registry.getPeppolId())).isEqualTo(new PeppolSettings(true));
        assertThat(service.getSettings(registry.getPeppolId())).isEqualTo(new PeppolSettings(true));

        assertThat(registry.getReceiveDocuments()).isTrue();
        assertThat(registry.getVariables()).containsOnlyKeys("companyId");
    }

    @Test
    void changedSettingsAreSavedInTheRegistry() {
        var repository = mock(RegistryRepository.class);
        var providers = mock(AccessPointServiceRegistry.class);
        var provider = mock(AccessPointServiceInterface.class);
        var registry = new Registry("0208:0123456789", AccessPoint.RECOMMAND, Map.of("companyId", "company-1"));
        when(repository.findByIdForSettings(registry.getPeppolId())).thenReturn(Optional.of(registry));
        when(providers.get(AccessPoint.RECOMMAND)).thenReturn(provider);
        when(provider.updateSettings(any(), any())).thenAnswer(invocation -> {
            invocation.<Registry>getArgument(0).setReceiveDocuments(false);
            return new PeppolSettings(false);
        });

        assertThat(new RegistryService(repository, providers).updateSettings(registry.getPeppolId(), new PeppolSettings(false)))
                .isEqualTo(new PeppolSettings(false));

        assertThat(registry.getReceiveDocuments()).isFalse();
        assertThat(registry.getVariables()).containsOnlyKeys("companyId");
    }

    @Test
    void failedProviderWritesPersistCacheInvalidationWithoutRemovingCompanyIdentity() {
        var repository = mock(RegistryRepository.class);
        var providers = mock(AccessPointServiceRegistry.class);
        var provider = mock(AccessPointServiceInterface.class);
        var registry = new Registry("0208:0123456789", AccessPoint.RECOMMAND,
                Map.of("companyId", "company-1"));
        registry.setReceiveDocuments(true);
        when(repository.findByIdForSettings(registry.getPeppolId())).thenReturn(Optional.of(registry));
        when(providers.get(AccessPoint.RECOMMAND)).thenReturn(provider);
        when(provider.updateSettings(any(), any())).thenAnswer(invocation -> {
            invocation.<Registry>getArgument(0).setReceiveDocuments(null);
            throw new IllegalStateException("Partial remote update");
        });

        assertThatThrownBy(() -> new RegistryService(repository, providers)
                .updateSettings(registry.getPeppolId(), new PeppolSettings(false)))
                .hasMessage("Partial remote update");

        assertThat(registry.getReceiveDocuments()).isNull();
        assertThat(registry.getVariables()).containsExactlyEntriesOf(Map.of("companyId", "company-1"));
    }

    @Test
    void removingSendingClearsReceivingWhileRetainingTheRegistrationResponse() {
        var repository = mock(RegistryRepository.class);
        var providers = mock(AccessPointServiceRegistry.class);
        var provider = mock(AccessPointServiceInterface.class);
        var registry = new Registry("0208:0123456789", AccessPoint.RECOMMAND, Map.of("companyId", "company-1"));
        registry.setReceiveDocuments(true);
        when(repository.findById(registry.getPeppolId())).thenReturn(Optional.of(registry));
        when(repository.save(registry)).thenReturn(registry);
        when(providers.get(AccessPoint.RECOMMAND)).thenReturn(provider);

        new RegistryService(repository, providers).unregister(registry.getPeppolId());

        assertThat(registry.getAccessPoint()).isEqualTo(AccessPoint.NONE);
        assertThat(registry.getReceiveDocuments()).isFalse();
        assertThat(registry.getVariables()).containsExactlyEntriesOf(Map.of("companyId", "company-1"));
        verify(provider).unregister(registry.getPeppolId(), registry.getVariables());
    }
}

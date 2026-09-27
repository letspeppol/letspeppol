package org.letspeppol.proxy.controller;

import org.junit.jupiter.api.Test;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.repository.RegistryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatsControllerTest {

    @Test
    void reportsOnlyCompaniesWithAnAccessPoint() {
        RegistryRepository repository = mock(RegistryRepository.class);
        when(repository.countByAccessPointNot(AccessPoint.NONE)).thenReturn(7L);

        assertThat(new StatsController(repository).getStats().activeCompanies()).isEqualTo(7L);
    }
}

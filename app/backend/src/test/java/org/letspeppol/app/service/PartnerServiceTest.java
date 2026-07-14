package org.letspeppol.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.dto.AddressDto;
import org.letspeppol.app.dto.PartnerDto;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.Partner;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.PartnerRepository;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerServiceTest {

    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final PartnerRepository partnerRepository = mock(PartnerRepository.class);
    private final Counter partnerCreateCounter = mock(Counter.class);
    private PartnerService partnerService;

    @BeforeEach
    void setUp() {
        partnerService = new PartnerService(companyRepository, partnerRepository, partnerCreateCounter);
        when(partnerRepository.save(any(Partner.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsPartnerWithTimesheetEnabled() {
        when(companyRepository.findByPeppolId("0208:owner")).thenReturn(Optional.of(new Company()));

        PartnerDto created = partnerService.createPartner("0208:owner", partnerDto(true));

        ArgumentCaptor<Partner> partnerCaptor = ArgumentCaptor.forClass(Partner.class);
        verify(partnerRepository).save(partnerCaptor.capture());
        assertThat(partnerCaptor.getValue().isTimesheet()).isTrue();
        assertThat(created.timesheet()).isTrue();
    }

    @Test
    void createsPartnerWithTimesheetDisabled() {
        when(companyRepository.findByPeppolId("0208:owner")).thenReturn(Optional.of(new Company()));

        PartnerDto created = partnerService.createPartner("0208:owner", partnerDto(false));

        assertThat(created.timesheet()).isFalse();
    }

    @Test
    void updatesTimesheetSetting() {
        Partner existing = new Partner();
        existing.setRegisteredOffice(new org.letspeppol.app.model.Address());
        when(partnerRepository.findById(1L)).thenReturn(Optional.of(existing));

        PartnerDto updated = partnerService.updatePartner(1L, partnerDto(true));

        assertThat(existing.isTimesheet()).isTrue();
        assertThat(updated.timesheet()).isTrue();
    }

    @Test
    void defaultsTimesheetToDisabledWhenJsonFieldIsMissing() throws Exception {
        PartnerDto partner = new ObjectMapper().readValue("""
                {
                  "name": "Customer",
                  "peppolId": "0208:0123456789",
                  "customer": true,
                  "supplier": false
                }
                """, PartnerDto.class);

        assertThat(partner.timesheet()).isFalse();
    }

    private PartnerDto partnerDto(boolean timesheet) {
        return new PartnerDto(
                null,
                "BE0123456789",
                "Customer",
                "customer@example.com",
                "0208:0123456789",
                true,
                false,
                timesheet,
                "Last day 30 days",
                "BE68539007547034",
                "Customer",
                new AddressDto(null, "Brussels", "1000", "Main Street 1", "BE")
        );
    }
}

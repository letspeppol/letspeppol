package org.letspeppol.app.service;

import io.micrometer.core.instrument.Counter;
import org.letspeppol.app.dto.PartnerDto;
import org.letspeppol.app.exception.NotFoundException;
import org.letspeppol.app.mapper.PartnerMapper;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.Partner;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class PartnerService {

    private final CompanyRepository companyRepository;
    private final PartnerRepository partnerRepository;
    private final Counter partnerCreateCounter;

    public List<PartnerDto> findByPeppolId(String peppolId) {
        return partnerRepository.findByOwningPeppolId(peppolId).stream()
                .map(PartnerMapper::toDto)
                .toList();
    }

    public PartnerDto createPartner(String peppolId, PartnerDto partnerDto) {
        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new NotFoundException("Company does not exist"));
        Partner partner = new Partner(
                partnerDto.vatNumber(),
                partnerDto.name(),
                partnerDto.email(),
                partnerDto.peppolId(),
                partnerDto.customer(),
                partnerDto.supplier(),
                partnerDto.paymentTerms(),
                partnerDto.iban(),
                partnerDto.paymentAccountName(),
                partnerDto.registeredOffice().city(),
                partnerDto.registeredOffice().postalCode(),
                partnerDto.registeredOffice().street(),
                partnerDto.registeredOffice().countryCode()
        );
        partner.setCompany(company);
        partner = partnerRepository.save(partner);
        partnerCreateCounter.increment();
        return PartnerMapper.toDto(partner);
    }

    public PartnerDto updatePartner(Long id, PartnerDto partnerDto) {
        Partner partner = partnerRepository.findById(id).orElseThrow(() -> new NotFoundException("Partner does not exist"));
        partner.setVatNumber(partnerDto.vatNumber());
        partner.setName(partnerDto.name());
        partner.setEmail(partnerDto.email());
        partner.setPeppolId(partnerDto.peppolId());
        partner.setCustomer(partnerDto.customer());
        partner.setSupplier(partnerDto.supplier());
        partner.setPaymentTerms(partnerDto.paymentTerms());
        partner.setIban(partnerDto.iban());
        partner.setPaymentAccountName(partnerDto.paymentAccountName());
        partner.getRegisteredOffice().setCity(partnerDto.registeredOffice().city());
        partner.getRegisteredOffice().setPostalCode(partnerDto.registeredOffice().postalCode());
        partner.getRegisteredOffice().setStreet(partnerDto.registeredOffice().street());
        partner.getRegisteredOffice().setCountryCode(partnerDto.registeredOffice().countryCode());
        partner = partnerRepository.save(partner);
        return PartnerMapper.toDto(partner);
    }

    public void deletePartner(Long id) {
        partnerRepository.deleteById(id);
    }
}

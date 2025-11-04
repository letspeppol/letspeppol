package io.tubs.app.service;

import io.tubs.app.dto.PartnerDto;
import io.tubs.app.exception.NotFoundException;
import io.tubs.app.mapper.PartnerMapper;
import io.tubs.app.model.Company;
import io.tubs.app.model.Partner;
import io.tubs.app.repository.CompanyRepository;
import io.tubs.app.repository.PartnerRepository;
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

    public List<PartnerDto> findByCompanyNumber(String companyNumber) {
        return partnerRepository.findByOwningCompany(companyNumber).stream()
                .map(PartnerMapper::toDto)
                .toList();
    }

    public PartnerDto createPartner(String companyNumber, PartnerDto partnerDto) {
        Company company = companyRepository.findByCompanyNumber(companyNumber).orElseThrow(() -> new NotFoundException("Company does not exist"));
        Partner partner = new Partner(
                partnerDto.companyNumber(),
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
                partnerDto.registeredOffice().houseNumber()
        );
        partner.setCompany(company);
        partnerRepository.save(partner);
        return PartnerMapper.toDto(partner);
    }

    public PartnerDto updatePartner(String companyNumber, Long id, PartnerDto partnerDto) {
        Partner partner = partnerRepository.findById(id).orElseThrow(() -> new NotFoundException("Partner does not exist"));
        partner.setCompanyNumber(companyNumber);
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
        partner.getRegisteredOffice().setHouseNumber(partnerDto.registeredOffice().houseNumber());
        partnerRepository.save(partner);
        return PartnerMapper.toDto(partner);
    }

    public void deletePartner(Long id) {
        partnerRepository.deleteById(id);
    }
}

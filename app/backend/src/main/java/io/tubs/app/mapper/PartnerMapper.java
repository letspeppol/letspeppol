package io.tubs.app.mapper;

import io.tubs.app.dto.PartnerDto;
import io.tubs.app.model.Partner;

public class PartnerMapper {

    public static PartnerDto toDto(Partner partner) {
        return new PartnerDto(
                partner.getId(),
                partner.getVatNumber(),
                partner.getName(),
                partner.getEmail(),
                partner.getPeppolId(),
                partner.getCustomer(),
                partner.getSupplier(),
                partner.getPaymentTerms(),
                partner.getIban(),
                partner.getPaymentAccountName(),
                AddressMapper.toDto(partner.getRegisteredOffice())
        );
    }

}

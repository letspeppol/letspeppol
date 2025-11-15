package org.letspeppol.app.mapper;

import org.letspeppol.app.dto.PartnerDto;
import org.letspeppol.app.model.Partner;

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

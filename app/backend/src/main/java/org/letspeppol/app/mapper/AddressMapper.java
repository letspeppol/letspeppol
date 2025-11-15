package org.letspeppol.app.mapper;

import org.letspeppol.app.dto.AddressDto;
import org.letspeppol.app.model.Address;

public class AddressMapper {

    public static AddressDto toDto(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressDto(
                address.getId(),
                address.getCity(),
                address.getPostalCode(),
                address.getStreet(),
                address.getHouseNumber(),
                address.getCountryCode()
        );
    }

}

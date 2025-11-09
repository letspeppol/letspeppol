package io.tubs.app.mapper;

import io.tubs.app.dto.AddressDto;
import io.tubs.app.model.Address;

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
                address.getHouseNumber()
        );
    }

}

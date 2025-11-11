package io.tubs.app.dto;

public record AddressDto(
        Long id,
        String city,
        String postalCode,
        String street,
        String houseNumber,
        String countryCode
)
{}

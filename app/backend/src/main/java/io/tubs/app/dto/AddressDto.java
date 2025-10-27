package io.tubs.app.dto;

public record AddressDto(
        String city,
        String postalCode,
        String street,
        String houseNumber
)
{}

package io.tubs.app.dto;

public record AppRegistrationRequest(
        String companyNumber,
        String companyName,
        String street,
        String houseNumber,
        String city,
        String postalCode,
        String directorName,
        String directorEmail
) {}

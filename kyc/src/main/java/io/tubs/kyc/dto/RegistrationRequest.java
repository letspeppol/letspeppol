package io.tubs.kyc.dto;

/**
 * Sent to App backend
 */
public record RegistrationRequest(
        String companyNumber,
        String companyName,
        String street,
        String houseNumber,
        String city,
        String postalCode,
        String directorName,
        String directorEmail
) {}

package io.tubs.app.dto;

public record PartnerDto(
    Long id,
    String vatNumber,
    String name,
    String email,
    String peppolId,
    Boolean customer,
    Boolean supplier,

    String paymentTerms,
    String iban,
    String paymentAccountName,
    AddressDto registeredOffice
)
{}

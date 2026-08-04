package org.letspeppol.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Currency;

public record SponsorInvoiceRequest(
        @NotNull @DecimalMin(value = "1.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull Currency currency,
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Size(max = 255) String message
) {}

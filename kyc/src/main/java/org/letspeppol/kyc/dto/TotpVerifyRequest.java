package org.letspeppol.kyc.dto;

import jakarta.validation.constraints.NotBlank;

public record TotpVerifyRequest(@NotBlank String code) {}

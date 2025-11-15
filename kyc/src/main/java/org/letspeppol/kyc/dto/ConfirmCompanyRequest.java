package org.letspeppol.kyc.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmCompanyRequest(
        @NotBlank @Size(max = 32) String companyNumber,
        @Email @NotBlank String email
) {}


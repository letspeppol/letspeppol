package org.letspeppol.kyc.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.letspeppol.kyc.model.AccountType;

public record ConfirmCompanyRequest(
        @NotNull AccountType type,
        @NotBlank @Size(max = 32) String peppolId,
        @Email @NotBlank String email,
        String city,
        String postalCode,
        String street
) {}


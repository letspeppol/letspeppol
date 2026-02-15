package org.letspeppol.kyc.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;

public record ConfirmCompanyRequest(
        Account requester,
        @NotBlank AccountType type,
        @NotBlank @Size(max = 32) String peppolId,
        @Email @NotBlank String email,
        String city,
        String postalCode,
        String street
) {}


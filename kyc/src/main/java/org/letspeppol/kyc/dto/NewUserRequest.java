package org.letspeppol.kyc.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record NewUserRequest(
        @NotBlank String name,
        @Email @NotBlank String email
) {}

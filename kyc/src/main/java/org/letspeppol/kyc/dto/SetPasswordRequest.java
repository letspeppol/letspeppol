package org.letspeppol.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8) String newPassword
) {}

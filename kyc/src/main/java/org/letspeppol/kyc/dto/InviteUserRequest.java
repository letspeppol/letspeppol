package org.letspeppol.kyc.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteUserRequest(
        @Email @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 255) String name,
        @NotNull Integer permissionMask
) {}

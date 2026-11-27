package org.letspeppol.kyc.dto;

import jakarta.validation.constraints.NotBlank;

public record AcceptInvitationRequest(
        @NotBlank String token,
        String newPassword
) {}

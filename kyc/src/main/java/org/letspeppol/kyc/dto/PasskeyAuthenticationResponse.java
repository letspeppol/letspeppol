package org.letspeppol.kyc.dto;

import jakarta.validation.constraints.NotBlank;

public record PasskeyAuthenticationResponse(
        @NotBlank String id,
        @NotBlank String rawId,
        @NotBlank String type,
        @NotBlank String clientDataJSON,
        @NotBlank String authenticatorData,
        @NotBlank String signature,
        String userHandle
) {}

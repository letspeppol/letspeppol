package org.letspeppol.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.letspeppol.kyc.model.AccountType;

public record AuthSwapRequest(
        @NotBlank AccountType type,
        @NotBlank @Size(max = 32) String peppolId
) {}

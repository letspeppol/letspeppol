package org.letspeppol.kyc.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.letspeppol.kyc.model.AccountType;

import java.util.UUID;

public record ServiceRequest(
        @Valid @NotBlank UUID uid
) {}

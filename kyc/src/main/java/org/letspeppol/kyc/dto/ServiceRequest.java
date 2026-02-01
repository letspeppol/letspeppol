package org.letspeppol.kyc.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ServiceRequest(
        @NotNull UUID uid
) {}

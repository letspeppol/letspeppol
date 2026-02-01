package org.letspeppol.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ServiceRequest(
        @NotNull UUID uid
) {}

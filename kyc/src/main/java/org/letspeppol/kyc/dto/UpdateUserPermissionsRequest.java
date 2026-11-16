package org.letspeppol.kyc.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserPermissionsRequest(
        @NotNull Integer permissionMask
) {}

package org.letspeppol.proxy.dto;

import org.letspeppol.proxy.model.AccessPoint;

public record RegistryDto(
    String peppolId,
    AccessPoint accessPoint
) {}

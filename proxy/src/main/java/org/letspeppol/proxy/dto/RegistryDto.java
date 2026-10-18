package org.letspeppol.proxy.dto;

public record RegistryDto(
        String peppolId,
        boolean peppolActive,
        String accessPoint,
        String defaultAccessPoint
) {
    public RegistryDto(String peppolId, boolean peppolActive, String accessPoint) {
        this(peppolId, peppolActive, accessPoint, null);
    }
}
